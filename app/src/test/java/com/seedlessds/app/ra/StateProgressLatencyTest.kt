package com.seedlessds.app.ra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StateProgressLatencyTest {
    @Test fun inactiveAchievementsNeverReadSidecarOrRehashStateFile() {
        var reads = 0
        repeat(20) {
            assertFalse(RetroAchievements.deserializeProgress({
                reads++
                error("An inactive achievement session must not access storage")
            }))
        }
        assertEquals(0, reads)
    }
}
