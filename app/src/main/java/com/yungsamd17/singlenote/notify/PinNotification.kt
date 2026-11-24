package com.yungsamd17.singlenote.notify

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.AppDatabase
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.util.ActionSecurity
import com.yungsamd17.singlenote.util.Diagnostics
import kotlinx.coroutines.flow.first

object PinNotification {

    // Internal so the channel migration can be tested.
    internal const val CHANNEL_ID = "pinned_note_v4"
    internal const val LEGACY_CHANNEL_ID = "pinned_note"
    internal const val LEGACY_CHANNEL_ID_V2 = "pinned_note_v2"
    internal const val LEGACY_CHANNEL_ID_V3 = "pinned_note_v3"
    internal const val NOTIFICATION_ID = 1
    private const val REQUEST_OPEN_APP = 2
    private const val REQUEST_QUICK_CAPTURE = 3

    private data class NoteIntents(
        val open: PendingIntent,
        val openAction: PendingIntent,
        val quickCapture: PendingIntent,
    )

    // The last posted content, cached so a lock event can re-post the concealed
    // version synchronously. Without this, reading DataStore + Room takes long
    // enough (hundreds of ms on HyperOS) that the full note is visible on the
    // lock screen for a moment before the async refresh catches up.
    @Volatile private var cachedContent: PinnedNotificationContent? = null
    @Volatile private var cachedIntents: NoteIntents? = null
    @Volatile private var cachedHideOnLockscreen: Boolean = false

    // Starts concealed: a notification posted before any unlock evidence must
    // never carry the note. Only USER_PRESENT or an unlocked resume flips it.
    @Volatile private var noteRevealed: Boolean = false

    // The app being on screen, unlocked, is itself proof the user can see the
    // note — e.g. pinning it right after a stale background conceal.
    @Volatile private var appInForeground: Boolean = false

    fun setAppInForeground(value: Boolean) {
        appInForeground = value
        if (value) noteRevealed = true
    }

    /** The last notification built, used as the foreground service notification. */
    @Volatile var lastNotification: Notification? = null
        private set

    /** Positive unlock signal: later refreshes may show the note again. */
    fun markRevealed() {
        noteRevealed = true
    }

    /** Any screen/lock event: hide immediately and keep hiding until unlocked. */
    fun markConcealed(context: Context) {
        noteRevealed = false
        concealNow(context)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Channels are immutable once created: the old LOW-importance
        // channels never surfaced on the lock screen (and the first two
        // exposed full note text), so the DEFAULT-importance channel below
        // gets a new id and the legacy ones are removed (their settings
        // would otherwise stick forever).
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_V2)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_V3)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_pinned_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_pinned_description)
                setShowBadge(false)
                // Channel default hides content on the lock screen; the
                // notification below stays PRIVATE unless the user opts
                // in via Settings.
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * @param forceConcealed set by [LockScreenReceiver] on both screen off and
     * screen on: neither event is proof the user is past the lock screen, so
     * the note stays hidden until USER_PRESENT arrives.
     */
    suspend fun refresh(context: Context, forceConcealed: Boolean = false) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val prefs = NotePreferences.get(context)

        val notificationsEnabled = prefs.notificationsEnabled.first()
        val pinned = prefs.pinned.first()
        val lockscreenVisible = prefs.lockscreenVisible.first()
        val titleFirstLine = prefs.notificationTitleFirstLine.first()
        val note = AppDatabase.get(context).noteDao().getActive()

        if (!notificationsEnabled || !pinned || note == null || note.content.isBlank()) {
            manager.cancel(NOTIFICATION_ID)
            cachedContent = null
            cachedIntents = null
            lastNotification = null
            PinnedNoteService.stop(context)
            return
        }
        if (!manager.areNotificationsEnabled()) return

        // Both entry points carry a flag so the activity returns to the note
        // screen instead of whatever page was left in the back stack.
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_FROM_NOTIFICATION, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAction = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_FROM_NOTIFICATION, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Quick capture from the shade: opens the app, archives the note on
        // screen (never deletes it) and puts the cursor in an empty editor.
        // It is an explicit activity start, so it also works on restrictive
        // ROMs where a background action button would go dead.
        val quickCaptureAction = PendingIntent.getActivity(
            context,
            REQUEST_QUICK_CAPTURE,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_QUICK_CAPTURE, true)
                .putExtra(ActionSecurity.EXTRA_TOKEN, ActionSecurity.token(context)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The title/body and lock-screen rules live in
        // PinnedNotificationContent.kt so they can be tested on the JVM. The
        // reveal flag is only set by a positive unlock signal; the keyguard
        // API is logged for diagnosis but never trusted to reveal the note.
        val keyguardLocked = context.getSystemService(KeyguardManager::class.java)
            ?.isKeyguardLocked ?: false
        // A background conceal queued before the app came back must not land
        // after a foreground refresh and leave the placeholder showing.
        val staleBackgroundConceal = forceConcealed && appInForeground && !keyguardLocked
        val effectiveForceConcealed = forceConcealed && !staleBackgroundConceal
        val content = pinnedNotificationContent(
            note = note,
            notificationsEnabled = notificationsEnabled,
            pinned = pinned,
            titleFirstLine = titleFirstLine,
            lockscreenVisible = lockscreenVisible,
            noteRevealed = noteRevealed || (appInForeground && !keyguardLocked),
            forceConcealed = effectiveForceConcealed,
            defaultTitle = context.getString(R.string.notification_pinned_title),
            hiddenBody = context.getString(R.string.notification_hidden_body),
        ) ?: return

        cachedContent = content
        cachedIntents = NoteIntents(openIntent, openAction, quickCaptureAction)
        cachedHideOnLockscreen = !lockscreenVisible

        Diagnostics.event(
            "pin",
            "refresh lockVisible=$lockscreenVisible keyguard=$keyguardLocked revealed=$noteRevealed " +
                "forceConcealed=$forceConcealed concealed=${content.body == context.getString(R.string.notification_hidden_body)}"
        )

        // A generic public version is attached to every post. On a secure
        // lock screen Android shows this instead of the full notification,
        // so the note (and its first-line title) cannot leak there even in
        // the instant before the keyguard receiver re-posts the concealed
        // version.
        val publicNotification = buildNotification(
            context = context,
            title = content.publicTitle,
            body = content.publicBody,
            visibility = NotificationCompat.VISIBILITY_PRIVATE,
            openIntent = openIntent,
            openAction = openAction,
            quickCaptureAction = quickCaptureAction,
            // The public version is what a lock screen renders: no buttons
            // or big text there, so neither an action nor a cached expanded
            // row can reveal the note from the lock screen.
            includeActions = false,
            expanded = false,
        )
        val concealedNow = content.body == context.getString(R.string.notification_hidden_body)
        val notification = buildNotification(
            context = context,
            title = content.title,
            body = content.body,
            visibility = content.visibility,
            openIntent = openIntent,
            openAction = openAction,
            quickCaptureAction = quickCaptureAction,
            publicVersion = publicNotification,
            includeActions = !concealedNow,
            expanded = !concealedNow,
        )

        lastNotification = notification
        try {
            manager.notify(NOTIFICATION_ID, notification)
            PinnedNoteService.updateNotification(notification)
        } catch (_: SecurityException) {
        }
    }

    /**
     * Re-posts the concealed version immediately, without any disk reads.
     *
     * Called synchronously from the activity lifecycle and from the receiver
     * before their async work starts, so the full note is never left posted
     * while the lock screen comes up. Only the cached generic fields are used:
     * no part of the note text is present in this notification, not even in
     * its public version or its expanded style.
     */
    fun concealNow(context: Context) {
        val content = cachedContent ?: return
        val intents = cachedIntents ?: return
        if (!cachedHideOnLockscreen) return
        val app = context.applicationContext
        try {
            val publicNotification = buildNotification(
                context = app,
                title = content.publicTitle,
                body = content.publicBody,
                visibility = NotificationCompat.VISIBILITY_PRIVATE,
                openIntent = intents.open,
                openAction = intents.openAction,
                quickCaptureAction = intents.quickCapture,
                includeActions = false,
                expanded = false,
            )
            val hidden = buildNotification(
                context = app,
                title = content.publicTitle,
                body = content.publicBody,
                visibility = NotificationCompat.VISIBILITY_PRIVATE,
                openIntent = intents.open,
                openAction = intents.openAction,
                quickCaptureAction = intents.quickCapture,
                publicVersion = publicNotification,
                includeActions = false,
                expanded = false,
            )
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, hidden)
            PinnedNoteService.updateNotification(hidden)
            Diagnostics.event("pin", "concealNow applied")
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(
        context: Context,
        title: String,
        body: String,
        visibility: Int,
        openIntent: PendingIntent,
        openAction: PendingIntent,
        quickCaptureAction: PendingIntent,
        publicVersion: Notification? = null,
        includeActions: Boolean = true,
        expanded: Boolean = true,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(visibility)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Re-post if the user dismissed the pin (some skins let ongoing
            // notifications be swiped away).
            .setDeleteIntent(PinActionReceiver.respawnIntent(context))
            .setShowWhen(false)
            .setContentIntent(openIntent)
        // A concealed post intentionally has no big text: some OEM lock
        // screens keep the previous expanded row in their render cache, and
        // leaving a style out gives them nothing stale to paint on wake.
        if (expanded) builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        if (includeActions) {
            // Both actions open the app on purpose: buttons that need
            // background work go dead on restrictive ROMs once the app is
            // closed, but an explicit open always works (user-initiated
            // activity start). They are omitted from concealed posts so
            // nobody can archive the note from the lock screen.
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_open),
                openAction
            )
            builder.addAction(
                R.drawable.ic_new_note,
                context.getString(R.string.notification_action_new_note),
                quickCaptureAction
            )
        }
        if (publicVersion != null) builder.setPublicVersion(publicVersion)
        return builder.build()
    }
}
