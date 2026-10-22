package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch

class LiveInputTest {
    private data class Snap(val pad: Int, val touch: Int, val turbo: Int)

    @Test fun cancellationOnlyDropsTheOwningDisplaysPen() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setTouch(true, 55, LiveInput.PRIMARY_TOUCH)
        assertFalse(state.cancelTouch(LiveInput.EXTERNAL_TOUCH))
        assertEquals(Int.MIN_VALUE, events.last().pad)
        assertTrue(state.cancelTouch(LiveInput.PRIMARY_TOUCH))
        assertEquals(0, events.last().pad)
        state.setTouch(true, 66, LiveInput.EXTERNAL_TOUCH)
        state.setTouch(false, 0, LiveInput.EXTERNAL_TOUCH)
        assertTrue(state.cancelTouch(LiveInput.EXTERNAL_TOUCH))
    }

    @Test fun identicalKeysOnDifferentPhysicalDevicesRemainIndependent() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButton(4, true, LiveInput.KEYBOARD, 10)
        state.setButton(4, true, LiveInput.KEYBOARD, 20)
        state.setButton(4, false, LiveInput.KEYBOARD, 10)
        assertEquals(16, events.last().pad)
        state.releaseDevice(10)
        assertEquals(16, events.last().pad)
        state.releaseDevice(20)
        assertEquals(0, events.last().pad)
    }

    @Test fun diagonalChangePublishesOneCompleteMask() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButtons(15, 1, LiveInput.JOYSTICK)
        state.setButtons(15, 10, LiveInput.JOYSTICK)
        assertEquals(listOf(1, 10), events.map { it.pad })
    }

    @Test fun unchangedJoystickUpdatesDoNotCallNativeAgain() {
        var calls = 0
        val state = LiveInput { _, _, _ -> calls++ }
        repeat(1000) { state.setButtons(63, 5, LiveInput.JOYSTICK) }
        assertEquals(1, calls)
    }

    @Test fun directionUpdatesPreserveOtherButtons() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButton(8, true)
        state.setButtons(15, 3)
        assertEquals(259, events.last().pad)
    }

    @Test fun oneDeviceReleaseCannotReleaseAnotherDeviceHeldButton() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButton(4, true, LiveInput.KEYBOARD)
        state.setButton(4, true, LiveInput.JOYSTICK)
        state.setButton(4, false, LiveInput.JOYSTICK)
        assertEquals(1, events.size)
        assertEquals(16, events.last().pad)
        state.setButton(4, false, LiveInput.KEYBOARD)
        assertEquals(0, events.last().pad)
    }

    @Test fun touchPublishesDownAndCoordinatesTogether() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButton(8, true)
        state.setTouch(true, (255 shl 16) or 191)
        assertEquals(Snap(Int.MIN_VALUE or 256, (255 shl 16) or 191, 0), events.last())
        state.setTouch(false, 999)
        assertEquals(Snap(256, 0, 0), events.last())
    }

    @Test fun inactiveDisplayCannotReleaseTheOtherDisplaysPen() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setTouch(true, 123, LiveInput.PRIMARY_TOUCH)
        state.setTouch(false, 0, LiveInput.EXTERNAL_TOUCH)
        assertEquals(1, events.size)
        state.setTouch(false, 0, LiveInput.PRIMARY_TOUCH)
        assertEquals(0, events.last().pad)
    }

    @Test fun releaseAllClearsEverySourceAndPen() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButton(0, true)
        state.setButton(1, true, LiveInput.KEYBOARD)
        state.setButton(2, true, LiveInput.JOYSTICK)
        state.setTouch(true, 77)
        state.releaseAll()
        assertEquals(Snap(0, 0, 0), events.last())
        val before = events.size
        state.releaseAll()
        assertEquals(before + 1, events.size)
    }

    @Test fun forcedRepublishResynchronizesAfterCoreInitialization() {
        var calls = 0
        val state = LiveInput { _, _, _ -> calls++ }
        state.setButton(3, true)
        state.setButton(3, true)
        assertEquals(1, calls)
        state.republish()
        assertEquals(2, calls)
    }

    @Test fun turboIsPreservedAcrossNeutralMenuRelease() {
        val events = mutableListOf<Snap>()
        val state = LiveInput { a, b, c -> events += Snap(a, b, c) }
        state.setButtons(0x3f0, 0x110)
        state.toggleAutofire()
        state.releaseAll()
        assertEquals(Snap(0, 0, 0x110), events.last())
    }

    @Test fun concurrentCallbacksNeverPublishMixedTouchStates() {
        val events = Collections.synchronizedList(mutableListOf<Snap>())
        val state = LiveInput { a, b, c -> events.add(Snap(a, b, c)); Thread.yield() }
        val gate = CountDownLatch(1)
        val jobs = (0..2).map { source -> Thread {
            gate.await()
            repeat(1000) { n ->
                if (source == 0) state.setTouch(n % 2 == 0, 0x00ff00bf)
                else state.setButton(source, n % 2 == 0, source)
            }
        }.apply { start() } }
        gate.countDown(); jobs.forEach { it.join() }
        for (event in events) {
            if (event.pad and Int.MIN_VALUE != 0) assertEquals(0x00ff00bf, event.touch)
            else assertEquals(0, event.touch)
        }
    }

    @Test fun configuredStickDeadzoneControlsActivation() {
        assertEquals(0, StickInput.direction(0.04f, 0.05f))
        assertEquals(1, StickInput.direction(0.06f, 0.05f))
        assertEquals(-1, StickInput.direction(-0.06f, 0.05f))
        assertEquals(0, StickInput.direction(0.4f, 0.5f))
        assertEquals(0, StickInput.direction(0f, 0f))
    }

    @Test fun invalidStickValuesAndFullDeadzoneStayNeutral() {
        assertEquals(0, StickInput.direction(Float.NaN, 0.05f))
        assertEquals(0, StickInput.direction(1f, 1f))
        assertEquals(0, StickInput.direction(0.4f, Float.NaN))
    }
}
