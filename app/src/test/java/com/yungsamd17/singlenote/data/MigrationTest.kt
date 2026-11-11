package com.yungsamd17.singlenote.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers MIGRATION_1_2 and MIGRATION_2_3 against a real SQLite database.
 *
 * The app shipped versions 1 and 2 before schema export existed, so there are
 * no historical JSON schemas for MigrationTestHelper. Each test builds the
 * legacy schema by hand, runs the production migrations through a real
 * [SupportSQLiteOpenHelper.onUpgrade], then diffs the resulting table/index
 * shape against a freshly created Room v3 database, and finally opens the
 * migrated file with Room to prove the DAO can read it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(tempFolder.newFolder(), "singlenote.db")
    }

    @After
    fun tearDown() {
        tempFolder.delete()
    }

    @Test
    fun migrate1To3CollapsesDuplicateActiveRowsAndBackfillsArchiveDate() {
        // The v1 bug this migration fixes: two rows can both be ACTIVE.
        openHelper(version = 1) { db ->
            db.execSQL(V1_CREATE_NOTES)
            db.execSQL(insert("older", updatedAt = 100, state = Note.STATE_ACTIVE))
            db.execSQL(insert("newest", updatedAt = 200, state = Note.STATE_ACTIVE))
            db.execSQL(insert("archived", updatedAt = 300, state = Note.STATE_ARCHIVED))
        }.close()

        openMigrated().use { db ->
            assertSchemaMatchesCurrentRoom(db)
            val rows = db.readNotes()
            assertEquals(3, rows.size)

            val newest = rows.first { it.content == "newest" }
            assertEquals(Note.STATE_ACTIVE, newest.state)
            assertEquals(Note.ACTIVE_SLOT, newest.activeSlot)
            assertNull(newest.archivedAt)

            val older = rows.first { it.content == "older" }
            assertEquals(Note.STATE_ARCHIVED, older.state)
            assertNull(older.activeSlot)
            // Archive timestamp is backfilled from updatedAt.
            assertEquals(100L, older.archivedAt)

            val alreadyArchived = rows.first { it.content == "archived" }
            assertEquals(Note.STATE_ARCHIVED, alreadyArchived.state)
            assertNull(alreadyArchived.activeSlot)
            assertEquals(300L, alreadyArchived.archivedAt)
        }
    }

    @Test
    fun migratedDatabaseKeepsTheSingleActiveRowGuard() {
        openHelper(version = 1) { db ->
            db.execSQL(V1_CREATE_NOTES)
            db.execSQL(insert("active", updatedAt = 10, state = Note.STATE_ACTIVE))
        }.close()

        openMigrated().use { db ->
            val failure = runCatching {
                db.execSQL(
                    "INSERT INTO notes (content, createdAt, updatedAt, state, activeSlot) " +
                        "VALUES ('second', 1, 1, ${Note.STATE_ACTIVE}, ${Note.ACTIVE_SLOT})"
                )
            }.exceptionOrNull()
            assertTrue(
                "a second ACTIVE row must violate the unique index, got $failure",
                failure is SQLiteConstraintException
            )
        }
    }

    @Test
    fun migrate2To3KeepsActiveSlotAndBackfillsOnlyArchivedRows() {
        openHelper(version = 2) { db ->
            db.execSQL(V2_CREATE_NOTES)
            db.execSQL(V2_CREATE_INDEX)
            db.execSQL(insert("active", updatedAt = 10, state = Note.STATE_ACTIVE))
            db.execSQL("UPDATE notes SET activeSlot = ${Note.ACTIVE_SLOT}")
            db.execSQL(insert("archived", updatedAt = 42, state = Note.STATE_ARCHIVED))
        }.close()

        openMigrated().use { db ->
            assertSchemaMatchesCurrentRoom(db)
            val rows = db.readNotes()
            val active = rows.first { it.content == "active" }
            assertEquals(Note.ACTIVE_SLOT, active.activeSlot)
            assertNull(active.archivedAt)
            val archived = rows.first { it.content == "archived" }
            assertNull(archived.activeSlot)
            assertEquals(42L, archived.archivedAt)
        }
    }

    @Test
    fun roomDaoReadsMigratedRows() {
        openHelper(version = 1) { db ->
            db.execSQL(V1_CREATE_NOTES)
            db.execSQL(insert("kept", updatedAt = 5, state = Note.STATE_ACTIVE))
            db.execSQL(insert("old", updatedAt = 4, state = Note.STATE_ARCHIVED))
        }.close()

        // The file is already at v3; opening it with Room proves the migrated
        // schema is the one the DAO queries expect.
        val database = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        try {
            val active = runBlocking { database.noteDao().getActive() }
            assertEquals("kept", active?.content)
            val archived = runBlocking { database.noteDao().observeArchived().first() }
            assertEquals(listOf("old"), archived.map { it.content })
        } finally {
            database.close()
        }
    }

    private fun openHelper(
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteOpenHelper {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        if (oldVersion < 2) MIGRATION_1_2.migrate(db)
                        if (oldVersion < 3) MIGRATION_2_3.migrate(db)
                    }
                })
                .build()
        )
        // Open once so onCreate/onUpgrade really runs.
        helper.writableDatabase
        return helper
    }

    private fun openMigrated(): SQLiteDatabase {
        // Re-open with the real migration path (v1/v2 file -> v3).
        openHelper(version = 3) { error("the database already exists") }.close()
        return SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
    }

    /** Compares the migrated table/index shape with a fresh Room-created v3 DB. */
    private fun assertSchemaMatchesCurrentRoom(migrated: SQLiteDatabase) {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val current = database.openHelper.writableDatabase
            assertEquals(
                "column shape drifted from the Room v3 schema",
                tableInfo(current),
                tableInfo(migrated)
            )
            assertEquals(
                "index shape drifted from the Room v3 schema",
                indexInfo(current),
                indexInfo(migrated)
            )
        } finally {
            database.close()
        }
    }

    private fun tableInfo(db: SupportSQLiteDatabase): List<String> =
        db.query("PRAGMA table_info(notes)").use { it.readColumns() }

    private fun tableInfo(db: SQLiteDatabase): List<String> =
        db.rawQuery("PRAGMA table_info(notes)", null).use { it.readColumns() }

    /**
     * Column name/type/nullability/default/pk, sorted by name: ALTER TABLE
     * appends new columns, so physical order differs from a fresh CREATE.
     */
    private fun Cursor.readColumns(): List<String> = buildList {
        while (moveToNext()) {
            add(
                listOf(
                    getString(getColumnIndexOrThrow("name")),
                    getString(getColumnIndexOrThrow("type")),
                    getInt(getColumnIndexOrThrow("notnull")),
                    getString(getColumnIndexOrThrow("dflt_value")) ?: "null",
                    getInt(getColumnIndexOrThrow("pk")),
                ).joinToString("|")
            )
        }
    }.sorted()

    private fun indexInfo(db: SupportSQLiteDatabase): List<String> =
        db.query("PRAGMA index_list(notes)").use { cursor ->
            indexEntries(cursor) { name ->
                db.query("PRAGMA index_info(`$name`)").use { it.readIndexColumns() }
            }
        }

    private fun indexInfo(db: SQLiteDatabase): List<String> =
        db.rawQuery("PRAGMA index_list(notes)", null).use { cursor ->
            indexEntries(cursor) { name ->
                db.rawQuery("PRAGMA index_info(`$name`)", null).use { it.readIndexColumns() }
            }
        }

    private fun indexEntries(cursor: Cursor, columns: (String) -> List<String>): List<String> {
        val entries = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique"))
            entries += "$name unique=$unique ${columns(name)}"
        }
        return entries.distinct().sorted()
    }

    /** Indexed column names only: the column id shifts when ALTER TABLE appends. */
    private fun Cursor.readIndexColumns(): List<String> = buildList {
        while (moveToNext()) {
            add(getString(getColumnIndexOrThrow("name")))
        }
    }

    private fun SQLiteDatabase.readNotes(): List<MigratedNote> =
        rawQuery(
            "SELECT content, state, activeSlot, archivedAt FROM notes ORDER BY id",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MigratedNote(
                            content = cursor.getString(0),
                            state = cursor.getInt(1),
                            activeSlot = if (cursor.isNull(2)) null else cursor.getInt(2),
                            archivedAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                        )
                    )
                }
            }
        }

    private fun insert(content: String, updatedAt: Long, state: Int): String =
        "INSERT INTO notes (content, createdAt, updatedAt, state) " +
            "VALUES ('$content', $updatedAt, $updatedAt, $state)"

    private data class MigratedNote(
        val content: String,
        val state: Int,
        val activeSlot: Int?,
        val archivedAt: Long?,
    )

    private companion object {
        const val V1_CREATE_NOTES =
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `state` INTEGER NOT NULL)"

        const val V2_CREATE_NOTES =
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `state` INTEGER NOT NULL, " +
                "`activeSlot` INTEGER)"

        const val V2_CREATE_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS index_notes_activeSlot ON notes(activeSlot)"
    }
}
