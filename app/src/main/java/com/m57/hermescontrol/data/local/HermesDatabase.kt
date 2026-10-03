package com.m57.hermescontrol.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [
        ChatMessageEntity::class,
        BotGroupRoomEntity::class,
        BotGroupMemberEntity::class,
        BotGroupMessageEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class HermesDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun botGroupDao(): BotGroupDao

    companion object {
        @Volatile
        private var instance: HermesDatabase? = null

        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_chat_messages_session_id_timestamp` " +
                            "ON `chat_messages` (`session_id`, `timestamp`)",
                    )
                }
            }

        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `chat_messages` ADD COLUMN `reasoning_text` TEXT NOT NULL DEFAULT ''",
                    )
                }
            }

        // A database created directly at schema 4 has no SQL default on
        // reasoning_text. Rebuild the table so Room sees the canonical schema
        // after adding attachments_json; a plain ALTER leaves schema 5 invalid.
        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `_new_chat_messages` (
                            `id` TEXT NOT NULL,
                            `session_id` TEXT NOT NULL,
                            `role` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `reasoning_text` TEXT NOT NULL DEFAULT '',
                            `timestamp` INTEGER NOT NULL,
                            `tool_name` TEXT,
                            `tool_status` TEXT,
                            `is_streaming` INTEGER NOT NULL,
                            `attachments_json` TEXT NOT NULL DEFAULT '[]',
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `_new_chat_messages` (
                            `id`, `session_id`, `role`, `content`,
                            `reasoning_text`, `timestamp`, `tool_name`,
                            `tool_status`, `is_streaming`, `attachments_json`
                        )
                        SELECT
                            `id`, `session_id`, `role`, `content`,
                            `reasoning_text`, `timestamp`, `tool_name`,
                            `tool_status`, `is_streaming`, '[]'
                        FROM `chat_messages`
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE `chat_messages`")
                    db.execSQL(
                        "ALTER TABLE `_new_chat_messages` RENAME TO `chat_messages`",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_chat_messages_session_id_timestamp` " +
                            "ON `chat_messages` (`session_id`, `timestamp`)",
                    )
                }
            }

        internal val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `tool_call_id` TEXT")
                    db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `display_kind` TEXT")
                }
            }

        // Local phone group chat v1: profile-scoped rooms, membership with
        // resolved member sessions, and merged transcripts. Membership and
        // messages cascade on room deletion.
        internal val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `bot_group_rooms` (
                            `id` TEXT NOT NULL,
                            `connection_profile_id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `bot_group_members` (
                            `room_id` TEXT NOT NULL,
                            `bot_name` TEXT NOT NULL,
                            `resolved_session_id` TEXT,
                            PRIMARY KEY(`room_id`, `bot_name`),
                            FOREIGN KEY(`room_id`) REFERENCES `bot_group_rooms`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_bot_group_members_room_id` " +
                            "ON `bot_group_members` (`room_id`)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `bot_group_messages` (
                            `id` TEXT NOT NULL,
                            `room_id` TEXT NOT NULL,
                            `sender` TEXT NOT NULL,
                            `from_user` INTEGER NOT NULL,
                            `content` TEXT NOT NULL,
                            `source_session_id` TEXT,
                            `timestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`room_id`) REFERENCES `bot_group_rooms`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_bot_group_messages_room_id_timestamp` " +
                            "ON `bot_group_messages` (`room_id`, `timestamp`)",
                    )
                }
            }

        fun get(context: Context): HermesDatabase =
            instance ?: synchronized(this) {
                // SQLCipher can't open plaintext SQLite databases — if an old
                // unencrypted DB exists (v1), delete it so Room + SQLCipher can
                // create an encrypted replacement from scratch.
                removeUnencryptedDatabase(context.getDatabasePath("hermes_control.db"))

                // Load SQLCipher native library before creating the factory
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(AuthManager.getDatabasePassword())

                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        HermesDatabase::class.java,
                        "hermes_control.db",
                    ).openHelperFactory(factory)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }

        /**
         * Removes a legacy plaintext SQLite database (v1) so an encrypted
         * replacement can be created. Returns true if the database was
         * removed. Its WAL/SHM sidecars are removed with it — they hold
         * plaintext pages that would otherwise outlive the main file.
         *
         * Fail closed (audit V6): deletion requires a positive plaintext
         * SQLite header match. An unreadable or incomplete header means the
         * file may be an encrypted database we failed to inspect, so it is
         * kept untouched.
         */
        internal fun removeUnencryptedDatabase(dbFile: File): Boolean {
            if (!dbFile.exists() || !isPlaintextSqliteDatabase(dbFile)) return false
            dbFile.delete()
            for (suffix in listOf("-wal", "-shm")) {
                File(dbFile.parentFile, dbFile.name + suffix).delete()
            }
            return true
        }

        /**
         * Returns true only when the file positively starts with the plaintext
         * SQLite magic header "SQLite format 3\0". Read errors, partial
         * headers, and anything else return false — the caller keeps the file.
         */
        private fun isPlaintextSqliteDatabase(file: File): Boolean =
            try {
                val header = ByteArray(16)
                file.inputStream().use { input ->
                    var read = 0
                    while (read < header.size) {
                        val n = input.read(header, read, header.size - read)
                        if (n < 0) return false // incomplete header: uncertain, keep
                        read += n
                    }
                }
                header.contentEquals("SQLite format 3\u0000".toByteArray())
            } catch (_: Exception) {
                false // unreadable: uncertain, keep
            }

        /** For testing — inject a custom instance. */
        fun setForTest(db: HermesDatabase?) {
            instance = db
        }
    }
}
