package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test

class ScreenGeometryTest {
    private fun panel(w: Int, h: Int, dpi: Float = 300f) = floatArrayOf(w.toFloat(), h.toFloat(), dpi, dpi, dpi)
    private fun assertSize(w: Float, h: Float, rect: FloatArray) {
        assertEquals(w, rect[2], 0.02f); assertEquals(h, rect[3], 0.02f)
    }

    @Test fun preservesFourByThree() {
        assertSize(1440f, 1080f, ScreenGeometry.rect(1920f, 1080f, true, false, false, false, 1))
    }

    @Test fun matchesBothDimensionsForCrossAspectPanels() {
        val a = panel(1920, 1080); val b = panel(1280, 1280)
        assertSize(1280f, 1080f, ScreenGeometry.rect(a[0], a[1], false, false, false, false, 1, true, a, b))
        assertSize(1280f, 1080f, ScreenGeometry.rect(b[0], b[1], false, false, false, false, 1, true, b, a))
    }

    @Test fun matchesPhysicalSizeAcrossDifferentDpi() {
        val a = panel(1920, 1080, 400f); val b = panel(1280, 960, 300f)
        val ar = ScreenGeometry.rect(a[0], a[1], true, false, false, false, 1, true, a, b)
        val br = ScreenGeometry.rect(b[0], b[1], true, false, false, false, 1, true, b, a)
        assertEquals(ar[2]/400f, br[2]/300f, 0.001f)
        assertEquals(ar[3]/400f, br[3]/300f, 0.001f)
        assertEquals(4f/3f, ar[2]/ar[3], 0.001f)
    }

    @Test fun integerPriorityNeverBecomesThreePointSevenFive() {
        val a = panel(1920, 1080, 400f); val b = panel(1280, 960, 300f)
        assertSize(1280f, 960f, ScreenGeometry.rect(a[0], a[1], true, true, false, false, 1, true, a, b))
        // The shared physical bounds allow 1080 pixels here; snap down to 4x.
        assertSize(1024f, 768f, ScreenGeometry.rect(b[0], b[1], true, true, false, false, 1, true, b, a))
    }

    @Test fun smallWindowDownscalesInsteadOfCropping() {
        assertSize(200f, 150f, ScreenGeometry.rect(200f, 150f, true, true, false, false, 1))
    }

    @Test fun invalidWindowProducesEmptyRect() {
        assertSize(0f, 0f, ScreenGeometry.rect(0f, 100f, true, true, false, false, 1))
        assertSize(0f, 0f, ScreenGeometry.rect(Float.NaN, 100f, true, true, false, false, 1))
    }

    @Test fun explicitStretchCannotOverrideIntegerScale() {
        assertSize(1280f, 960f, ScreenGeometry.rect(1920f, 1080f, true, true, true, true, 1))
        assertSize(1920f, 1080f, ScreenGeometry.rect(1920f, 1080f, true, false, true, false, 1))
    }

    @Test fun alignmentUsesActualImageHeight() {
        val top = ScreenGeometry.rect(1000f, 1000f, true, false, false, false, 0)
        val bottom = ScreenGeometry.rect(1000f, 1000f, true, false, false, false, 2)
        assertEquals(0f, top[1], 0.001f); assertEquals(250f, bottom[1], 0.001f)
    }

    @Test fun unknownDensityFallsBackSymmetrically() {
        val a = floatArrayOf(1920f, 1080f, 0f, 0f, 0f); val b = panel(1280, 960)
        val ar = ScreenGeometry.rect(a[0], a[1], true, false, false, false, 1, true, a, b)
        val br = ScreenGeometry.rect(b[0], b[1], true, false, false, false, 1, true, b, a)
        assertSize(br[2], br[3], ar)
    }

    @Test fun swapControlsActualExternalTouchScreen() {
        assertEquals(0, ScreenGeometry.externalScreen(0, false))
        assertEquals(1, ScreenGeometry.externalScreen(0, true))
        assertEquals(1, ScreenGeometry.externalScreen(1, false))
        assertEquals(0, ScreenGeometry.externalScreen(1, true))
    }

    @Test fun variedPanelsStayInsideBoundsAndKeepRatio() {
        val panels = listOf(panel(200, 150), panel(1080, 1920, 400f), panel(1280, 960), panel(1920, 1080), panel(1280, 1280))
        for (a in panels) for (b in panels) for (integer in listOf(false, true)) {
            val r = ScreenGeometry.rect(a[0], a[1], true, integer, false, false, 2, true, a, b)
            assertTrue(r.all { it.isFinite() && it >= 0f })
            assertTrue(r[0] + r[2] <= a[0] + 0.02f)
            assertTrue(r[1] + r[3] <= a[1] + 0.02f)
            assertEquals(4f/3f, r[2]/r[3], 0.001f)
        }
    }
}
