package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class MbakTest {
    @Test fun roundTrip() {
        val json = """{"accounts":[],"budgets":[],"categories":[],"records":[]}"""
        assertEquals(json, Mbak.decrypt(Mbak.encrypt(json)))
    }

    @Test fun tamperedFileRejected() {
        val enc = Mbak.encrypt("{}")
        val parts = enc.split(":").toMutableList()
        parts[2] = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { 1 })
        assertThrows(IllegalArgumentException::class.java) { Mbak.decrypt(parts.joinToString(":")) }
    }

    /** Decrypts a real MyMoney backup (only runs when a backup path is supplied). */
    @Test fun realBackupDecrypts() {
        val f = File(System.getProperty("mbak.sample") ?: "")
        assumeTrue(f.isFile)
        val txt = Mbak.decrypt(f.readText())
        assertTrue(txt.startsWith("{\"accounts\":"))
    }
}
