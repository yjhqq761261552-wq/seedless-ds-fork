package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test

class DsPenPointerTest {
    private val rect = floatArrayOf(10f, 20f, 1280f, 960f)

    @Test fun allPixelCentersMapToTheCorrectDsCell() {
        for (y in 0..191) for (x in 0..255) {
            val actual = DsTouchCoordinates.map(10f + (x + 0.5f) * 5f, 20f + (y + 0.5f) * 5f, rect)
            assertEquals((x shl 16) or y, actual)
        }
    }

    @Test fun rightBottomInteriorReachesLastColumnAndRow() {
        assertEquals((255 shl 16) or 191, DsTouchCoordinates.map(1289f, 979f, rect))
        assertEquals((255 shl 16) or 191, DsTouchCoordinates.map(1290f, 980f, rect))
        assertEquals(0, DsTouchCoordinates.map(10f, 20f, rect))
    }

    @Test fun invalidAndOutsideCoordinatesAreRejected() {
        assertNull(DsTouchCoordinates.map(9f, 20f, rect))
        assertNull(DsTouchCoordinates.map(Float.NaN, 20f, rect))
        assertNull(DsTouchCoordinates.map(10f, 20f, floatArrayOf(0f, 0f, 0f, 1f)))
        assertNull(DsTouchCoordinates.map(10f, 20f, floatArrayOf(0f)))
    }

    @Test fun anotherFingerNeverStealsThePen() {
        val events = mutableListOf<Pair<Boolean, Int>>()
        val pen = DsPenPointer { a, b -> events += a to b }
        assertTrue(pen.down(7, 100f, 100f, rect))
        assertFalse(pen.down(9, 200f, 200f, rect))
        assertFalse(pen.move(9, 250f, 250f, rect))
        pen.up(9)
        assertEquals(7, pen.activeId)
        assertEquals(1, events.size)
    }

    @Test fun primaryLiftDoesNotTransferToRemainingFinger() {
        val events = mutableListOf<Pair<Boolean, Int>>()
        val pen = DsPenPointer { a, b -> events += a to b }
        pen.down(7, 100f, 100f, rect)
        pen.down(9, 200f, 200f, rect)
        pen.up(7)
        assertEquals(-1, pen.activeId)
        assertFalse(pen.move(9, 210f, 210f, rect))
        assertEquals(false to 0, events.last())
    }

    @Test fun leaveAndReenterUpdatesTheSamePointerWithoutSticking() {
        val events = mutableListOf<Pair<Boolean, Int>>()
        val pen = DsPenPointer { a, b -> events += a to b }
        pen.down(7, 100f, 100f, rect)
        pen.move(7, 0f, 0f, rect)
        assertEquals(false to 0, events.last())
        pen.move(7, 120f, 120f, rect)
        assertTrue(events.last().first)
        pen.cancel()
        assertEquals(-1, pen.activeId)
        assertEquals(false to 0, events.last())
    }
}
