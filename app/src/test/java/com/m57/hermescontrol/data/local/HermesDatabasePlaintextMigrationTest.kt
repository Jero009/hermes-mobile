package com.m57.hermescontrol.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Verifies the fail-closed plaintext→SQLCipher migration guard:
 *
 * - Audit V5: when a legacy unencrypted database is removed, its SQLite
 *   WAL/SHM sidecars are removed with it, so plaintext pages never linger.
 * - Audit V6: an unreadable or otherwise uncertain header keeps the database.
 *   Deletion requires a *positive* plaintext SQLite header match.
 */
class HermesDatabasePlaintextMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val plaintextHeader = "SQLite format 3\u0000".toByteArray()

    // ── Audit V5: sidecars go with the plaintext database ──

    @Test
    fun plaintextDatabaseMigrationRemovesWalAndShmSidecars() {
        val db = tmp.newFile("hermes_control.db").apply { writeBytes(plaintextHeader) }
        val wal = tmp.newFile("hermes_control.db-wal").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val shm = tmp.newFile("hermes_control.db-shm").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val removed = HermesDatabase.removeUnencryptedDatabase(db)

        assertTrue(removed)
        assertFalse(db.exists())
        assertFalse(wal.exists())
        assertFalse(shm.exists())
    }

    @Test
    fun encryptedDatabaseKeepsFileAndSidecars() {
        val db = tmp.newFile("hermes_control.db").apply { writeBytes(Random.nextBytes(32)) }
        val wal = tmp.newFile("hermes_control.db-wal").apply { writeBytes(byteArrayOf(1)) }
        val shm = tmp.newFile("hermes_control.db-shm").apply { writeBytes(byteArrayOf(2)) }

        assertFalse(HermesDatabase.removeUnencryptedDatabase(db))

        assertTrue(db.exists())
        assertTrue(wal.exists())
        assertTrue(shm.exists())
    }

    // ── Audit V6: fail closed on uncertain headers ──

    @Test
    fun unreadableHeaderKeepsDatabase() {
        // A directory with the database name exists() but its header cannot be
        // read — uncertain, so the migration must keep it untouched.
        val dbDir = tmp.newFolder("hermes_control.db")

        assertFalse(HermesDatabase.removeUnencryptedDatabase(dbDir))
        assertTrue(dbDir.exists())
    }

    @Test
    fun truncatedHeaderKeepsDatabase() {
        val db = tmp.newFile("hermes_control.db").apply { writeBytes("abc".toByteArray()) }

        assertFalse(HermesDatabase.removeUnencryptedDatabase(db))
        assertTrue(db.exists())
    }

    @Test
    fun missingDatabaseIsNoOp() {
        val db = File(tmp.root, "absent.db")

        assertFalse(HermesDatabase.removeUnencryptedDatabase(db))
        assertFalse(db.exists())
    }
}
