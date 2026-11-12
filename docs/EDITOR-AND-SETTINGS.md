# Editor, settings and widget behavior

## Note editor

The note card starts at the existing font-size-dependent minimum height and grows
with laid-out note content until it reaches the available screen space. Longer
content scrolls inside the card. Typing, pasting, moving the caret and keyboard /
font / viewport changes bring the active line into view with a small margin.
Manual scrolling remains available between edits; the viewport does not jump to
the end on every keystroke. Selection, IME composition, list-prefix insertion,
character limits and debounce/stop saving retain their existing behavior. The
character indicator appears after 500 characters; it shows the remaining count
at 20 or fewer, and gives visual and haptic feedback at the limit.

The bottom action follows keyboard insets. Keyboard dismissal keeps the input
connection focused until the keyboard is fully closed and settled. A quick
reopen cancels pending focus clearing. Preserve the Done/Pinned fade timing when
changing layout; early focus clearing can change the keyboard layout mid-dismissal.

## Settings layout and scrolling

The hub links to Appearance, Typography, Language, Notifications and About. Each
page uses a large top bar. A page whose complete content fits below the bar does
not scroll. Overflowing pages scroll under the pinned bar, which never collapses
or hides; increasing the viewport enough to fit the page leaves the bar unchanged.
This applies to the hub and all settings sub-screens, including About.

Groups use 2 dp gaps and segmented cards with 28 dp outer corners and 4 dp connecting corners, with surface container cards on a surface background. Main and archived note
cards use the same 28 dp radius as the 56 dp editor action buttons. Category headers use the primary color.
Switch cards toggle across their full row; disabled controls are dimmed and inert.

## Settings areas

| Page | Controls and behavior |
|---|---|
| Appearance | Existing theme/accent choices, wallpaper colors on Android 12+, pure-black dark background, and default, preset-accent, or wallpaper-accent launcher icon on Android 12+ |
| Typography | Bundled Google Sans Flex or system app font; existing default/mono/serif note style and text sizes |
| Language | Translation contribution link; System plus English, then the rest alphabetically by their English name (Deutsch, Español, Français, Italiano, Nederlands, Polski, Português (Brasil), Русский, Slovenčina, Türkçe) with Chinese (Simplified) last, each row with a round region badge, the English name on top of the native one and a check mark in the badge of the selected row |
| Notifications | Pinned notification and lock-screen content; lock-screen switch is disabled when notifications are off. The pinned notification carries an **Open** and a **New note** action; the quick capture archives the note on screen (never deletes it) and opens an empty editor with the cursor in it |
| About | Accent brand/version/update card, app description, legal links, project/support links, and Changelog last |

Dynamic colors disable manual accent selection while active. Turning them off
preserves the saved accent. AMOLED affects dark mode. The accent picker contains
only preset colors, each with a round color preview next to its name (grayed
along with the app in monochrome mode); Dynamic colors is the single
wallpaper-color control. Legacy app Material You selections enable that toggle
unless an explicit choice was already saved. Launcher aliases can follow a preset accent or the Android 12+ wallpaper accent.
A changed alias closes the app; reopen it from the launcher. Launcher caching can
delay updates.
Themed monochrome icons are controlled by Android/the launcher.

## Archive

The complete archived note is shown in each archive card. Archive and settings
lists leave 32 dp after their final item. Restore conflicts offer **Cancel**,
**Swap**, then **Replace**, in that order; the actions wrap on narrow/large-text
screens. Replace and Swap return to main with an Undo snackbar. Undo restores the
previous active note and returns the incoming note to the archive, retaining any
edits made to it during the undo window. Replace recovery retains the old note's
ID and timestamps; a stale Undo cannot overwrite a newer active note.

Archive cards show relative archive or edit times and support persisted sort
choices. JSON import/export and plain-text export are described in
[ARCHIVE.md](ARCHIVE.md).

App snackbars use rounded pill styling and a slightly lighter tonal action
button. Undo is offered for reversible note operations. The About update snackbar
uses the same styling and keeps its Download action.

## Widget

Widget appearance controls share the segmented card style. Each widget has its
own saved theme/accent, note/title sizes, title visibility, opacity and tap action.
The settings of the last widget are remembered as well, so removing a widget and
adding it again starts with the same settings instead of the defaults.

Long widget notes scroll inside the card with no visible scroll indicator. Tap
anywhere on the card, including blank space, to run its selected action. The
customization screen starts with a static preview so placeholder text is visible
without a widget host. Legacy per-widget Material You choices now follow the app.
Android launchers control widget dimensions;
resize the widget in the launcher to display more content at once.

## UI verification checklist

Check short/long notes, middle/end typing, cursor movement, multiline paste,
keyboard resize/dismiss/reopen, and manual scrolling. Test settings on a tall
screen where the page fits and a smaller/large-text viewport where it overflows.
Resize/reopen after scrolling to confirm the bar stays pinned. Inspect each locale
you touched and light/dark/AMOLED colors. For locale changes, verify the current
note survives and the themed snapshot fades cleanly. For widget scrolling and
launcher icon switching, test on an actual launcher.

Build/install commands are in [BUILDING.md](BUILDING.md). Contributor review
requirements are in [CONTRIBUTING.md](../CONTRIBUTING.md).
