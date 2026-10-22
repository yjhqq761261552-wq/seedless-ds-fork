package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class StickInputTest {
    private val up = StickInput.UP
    private val down = StickInput.DOWN
    private val left = StickInput.LEFT
    private val right = StickInput.RIGHT

    @Test fun horizontalPushWithVerticalDriftDoesNotSendUpOrDown() {
        for (x in listOf(-1f, -.8f, .8f, 1f)) for (y in listOf(-.2f, -.06f, 0f, .06f, .2f)) {
            assertEquals("x=$x y=$y", if (x < 0f) left else right, StickInput.buttons(x, y, .05f))
        }
    }
    @Test fun verticalPushWithHorizontalDriftDoesNotSendLeftOrRight() {
        for (y in listOf(-1f, -.8f, .8f, 1f)) for (x in listOf(-.2f, -.06f, 0f, .06f, .2f)) {
            assertEquals(if (y < 0f) up else down, StickInput.buttons(x, y, .05f))
        }
    }
    @Test fun deliberateDiagonalsRemainAvailableInAllFourQuadrants() {
        assertEquals(up or left, StickInput.buttons(-.8f, -.8f, .05f))
        assertEquals(up or right, StickInput.buttons(.8f, -.8f, .05f))
        assertEquals(down or left, StickInput.buttons(-.8f, .8f, .05f))
        assertEquals(down or right, StickInput.buttons(.8f, .8f, .05f))
    }
    @Test fun eightAngularSectorsHaveCorrectDirectionBits() {
        val expected = listOf(right, right or down, down, down or left, left, left or up, up, up or right)
        for (i in expected.indices) for (offset in listOf(-20.0, 0.0, 20.0)) {
            val angle = Math.toRadians(i * 45.0 + offset)
            assertEquals("angle=${i * 45 + offset}", expected[i], StickInput.buttons(cos(angle).toFloat(), sin(angle).toFloat(), .05f))
        }
    }
    @Test fun deviceFlatFiltersDriftEvenWhenUserDeadzoneIsSmaller() {
        assertEquals(0, StickInput.buttons(.1f, -.11f, .05f, .15f, .15f))
        assertEquals(right, StickInput.buttons(.8f, -.11f, .05f, .15f, .15f))
        assertEquals(up, StickInput.buttons(.1f, -.8f, .05f, .15f, .15f))
    }
    @Test fun largerUserDeadzoneIsRespected() {
        assertEquals(0, StickInput.buttons(.4f, -.4f, .5f, .1f, .1f))
        assertEquals(right, StickInput.buttons(.6f, -.4f, .5f, .1f, .1f))
        assertEquals(0, StickInput.buttons(1f, 1f, 1f))
    }
    @Test fun hatNoiseCannotOverrideAHorizontalStickPush() {
        for (hatY in listOf(-1f, -.06f, .06f, 1f)) {
            assertEquals(right, StickInput.controllerButtons(.9f, .08f, 0f, hatY, .05f))
        }
    }
    @Test fun hatWorksWhenStickIsNeutralAndSubthresholdHatNoiseIsIgnored() {
        assertEquals(left, StickInput.controllerButtons(.02f, -.03f, -1f, 0f, .05f))
        assertEquals(up, StickInput.controllerButtons(0f, 0f, 0f, -1f, .05f))
        assertEquals(0, StickInput.controllerButtons(0f, 0f, .1f, -.1f, .05f))
    }
    @Test fun fourWayModeSelectsDominantAxis() {
        assertEquals(right, StickInput.buttons(.9f, -.7f, .05f, fourWay = true))
        assertEquals(up, StickInput.buttons(.7f, -.9f, .05f, fourWay = true))
        assertEquals(left, StickInput.buttons(-.8f, .8f, .05f, fourWay = true))
    }
    @Test fun realHatPressIsNotStolenBySmallStickCenterDrift() {
        assertEquals(left, StickInput.controllerButtons(.06f, -.08f, -1f, 0f, .05f))
        assertEquals(up, StickInput.controllerButtons(.08f, .06f, 0f, -1f, .05f))
    }
    @Test fun slowStickPushStillRespondsAtConfiguredDeadzoneWithoutAnActiveHat() {
        assertEquals(right, StickInput.controllerButtons(.06f, 0f, 0f, 0f, .05f))
        assertEquals(right, StickInput.controllerButtons(.06f, 0f, 0f, -.1f, .05f))
    }
    @Test fun invalidAxisSamplesDoNotLatchDirection() {
        assertEquals(0, StickInput.buttons(Float.NaN, Float.POSITIVE_INFINITY, .05f))
        assertEquals(right, StickInput.buttons(.9f, Float.NaN, .05f))
        assertEquals(0, StickInput.buttons(.4f, .4f, Float.NaN))
    }
    @Test fun returningToCenterReleasesDirectionsAndPreservesOtherSources() {
        var pad = 0
        val live = LiveInput { next, _, _ -> pad = next }
        live.setButton(4, true, LiveInput.KEYBOARD)
        live.setButtons(15, StickInput.buttons(.9f, .08f, .05f), LiveInput.JOYSTICK)
        assertEquals(16 or right, pad)
        live.setButtons(15, StickInput.buttons(0f, 0f, .05f), LiveInput.JOYSTICK)
        assertEquals(16, pad)
    }
    @Test fun fullCircleNeverGeneratesOppositeDirectionsOrDiagonalsInFourWayMode() {
        for (degree in 0 until 3600) {
            val angle = Math.toRadians(degree / 10.0)
            val x = cos(angle).toFloat(); val y = sin(angle).toFloat()
            val mask = StickInput.buttons(x, y, .05f)
            assertFalse((mask and (up or down)) == (up or down))
            assertFalse((mask and (left or right)) == (left or right))
            val four = StickInput.buttons(x, y, .05f, fourWay = true)
            assertTrue(four != 0 && four and (four - 1) == 0)
        }
    }
}
