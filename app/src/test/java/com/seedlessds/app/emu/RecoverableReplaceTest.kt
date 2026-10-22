package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test

class RecoverableReplaceTest {
    private class Provider : RecoverableReplace.Files {
        val data = mutableMapOf("temp" to "new", "slot" to "old")
        val fail = mutableSetOf<Pair<String, String>>()
        var cleanupFails = false
        override fun exists(name: String) = name in data
        override fun move(from: String, to: String): Boolean {
            if (from to to in fail || to in data) return false
            data[to] = data.remove(from) ?: return false
            return true
        }
        override fun delete(name: String) = if (cleanupFails) false else data.remove(name) != null
    }
    private val backup = "slot" + RecoverableReplace.SUFFIX

    @Test fun successfulCommitPublishesNewContent() {
        val p = Provider()
        assertTrue(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals(mapOf("slot" to "new"), p.data)
    }
    @Test fun failedOldRenameDoesNotTouchOldContent() {
        val p = Provider(); p.fail += "slot" to backup
        assertFalse(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals("old", p.data["slot"])
        assertEquals("new", p.data["temp"])
    }
    @Test fun failedNewRenameRollsBackOldSlot() {
        val p = Provider(); p.fail += "temp" to "slot"
        assertFalse(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals("old", p.data["slot"])
    }
    @Test fun failedRollbackKeepsOldContentAtRecoveryName() {
        val p = Provider(); p.fail += "temp" to "slot"; p.fail += backup to "slot"
        assertFalse(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals("old", p.data[backup])
        p.fail.clear()
        assertTrue(RecoverableReplace.recover(p, "slot"))
        assertEquals("old", p.data["slot"])
    }
    @Test fun interruptedOldRenameIsRecoveredBeforeNextCommit() {
        val p = Provider(); p.move("slot", backup)
        p.fail += "temp" to "slot"
        assertFalse(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals("old", p.data["slot"])
    }
    @Test fun cleanupFailureDoesNotTurnACommittedSaveIntoFailure() {
        val p = Provider(); p.cleanupFails = true
        assertTrue(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals("new", p.data["slot"])
        assertEquals("old", p.data[backup])
    }
    @Test fun missingTempNeverRemovesPreviousContent() {
        val p = Provider(); p.data.remove("temp")
        assertFalse(RecoverableReplace.commit(p, "temp", "slot"))
        assertEquals("old", p.data["slot"])
    }
    @Test fun existingSlotIsNeverReplacedByRecoveryCopy() {
        val p = Provider(); p.data[backup] = "older"
        assertTrue(RecoverableReplace.recover(p, "slot"))
        assertEquals("old", p.data["slot"])
    }
}
