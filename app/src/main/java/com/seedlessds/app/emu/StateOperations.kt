package com.seedlessds.app.emu

import com.seedlessds.core.SeedlessCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Single admission point for UI and hotkeys. Cancellation never releases the
 * slot while native I/O is still running, including activity shutdown. */
internal class StateOperations(private val info: SaveStateInfo) {
    private val lock = Mutex()
    @Volatile private var closing = false
    fun cancelPending() { closing = true }
    fun awaitIdle() { kotlinx.coroutines.runBlocking { lock.withLock {} } }
    suspend fun run(slot: Int, load: Boolean): Boolean {
        if (closing || !lock.tryLock()) return false
        try { return execute(slot, load) } finally { lock.unlock() }
    }
    private suspend fun execute(slot: Int, load: Boolean): Boolean {
        return withContext(Dispatchers.IO + NonCancellable) {
            val ra = com.seedlessds.app.ra.RetroAchievements
            ra.beginStateLoad()
            SeedlessCore.stateHold(true)
            try {
                val held = withTimeoutOrNull(5000) {
                    while (!SeedlessCore.stateHeld()) { if (closing) return@withTimeoutOrNull false; delay(4) }
                    true
                } == true
                if (!held) { com.seedlessds.app.AppLog.w("state", "State pause acknowledgement timed out"); return@withContext false }
                val progress = if (load) null else ra.serializeProgress()
                val ticket = SeedlessCore.stateRequest(slot, load)
                if (ticket == 0L) { com.seedlessds.app.AppLog.w("state", "State request busy"); return@withContext false }
                var status: Int
                do { delay(4); status = SeedlessCore.stateRequestStatus(ticket) } while (status == 0)
                com.seedlessds.app.AppLog.i("state", "${if (load) "load" else "save"} slot=${slot + 1} completed=$status")
                if (status != 1) return@withContext false
                if (load) ra.deserializeProgress(info.readSidecar(slot))
                else if (progress == null || !info.writeSidecar(slot, progress)) info.deleteSidecar(slot)
                true
            } catch (error: Exception) {
                com.seedlessds.app.AppLog.e("state", "State operation failed: ${error.javaClass.simpleName}")
                false
            } finally {
                SeedlessCore.stateHold(false)
                ra.endStateLoad()
            }
        }
    }
}
