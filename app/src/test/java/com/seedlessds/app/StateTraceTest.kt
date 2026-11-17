package com.seedlessds.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class StateTraceTest {
    @Test fun stateDiagnosticsSurviveClosingNormalLogging() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "state-operations.log")
        AppLog.start(context, false)
        AppLog.stateTrace("save slot=9 begin")
        AppLog.stateTrace("save slot=9 phase=writer elapsed=1000ms")
        AppLog.close()
        assertTrue(file.readText(Charsets.UTF_8).contains("phase=writer elapsed=1000ms"))
    }

    @Test fun stateHistoryIsBoundedAndKeepsNewestRecord() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "state-operations.log")
        AppLog.start(context, false)
        file.writeText("old-record\n".repeat(5000), Charsets.UTF_8)
        AppLog.stateTrace("save slot=9 completed=1")
        assertTrue(file.length() < 32768)
        assertTrue(file.readText(Charsets.UTF_8).endsWith("save slot=9 completed=1\n"))
    }

    @Test fun concurrentBackgroundRecordsRemainComplete() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "state-operations.log")
        AppLog.start(context, false)
        file.writeText("", Charsets.UTF_8)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val jobs = (0 until 3).map { worker ->
                executor.submit {
                    repeat(80) { index -> AppLog.stateTrace("worker=$worker event=$index") }
                }
            }
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
            val records = file.readLines(Charsets.UTF_8).map { it.substringAfter(' ') }
            org.junit.Assert.assertEquals(240, records.size)
            org.junit.Assert.assertEquals(240, records.toSet().size)
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
