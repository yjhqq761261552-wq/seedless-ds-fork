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

    @Test fun transformIdentityReturnsSameRect() {
        val r = floatArrayOf(100f, 50f, 800f, 600f)
        val out = ScreenGeometry.transform(r, 1920f, 1080f, 1f, 0f, 0f)
        assertArrayEquals(r, out, 0.001f)
    }

    @Test fun transformScalesAboutViewCentre() {
        // Rect centred in the view stays centred and doubles in size.
        val out = ScreenGeometry.transform(floatArrayOf(560f, 240f, 800f, 600f), 1920f, 1080f, 2f, 0f, 0f)
        assertEquals(160f, out[0], 0.001f); assertEquals(-60f, out[1], 0.001f)
        assertSize(1600f, 1200f, out)
    }

    @Test fun transformOffsetIsFractionOfView() {
        val out = ScreenGeometry.transform(floatArrayOf(0f, 0f, 800f, 600f), 1000f, 500f, 1f, 0.1f, -0.2f)
        assertEquals(100f, out[0], 0.001f); assertEquals(-100f, out[1], 0.001f)
        assertSize(800f, 600f, out)
    }

    @Test fun transformClampsExtremeValues() {
        val out = ScreenGeometry.transform(floatArrayOf(0f, 0f, 100f, 75f), 1000f, 500f, 99f, 5f, -5f)
        assertSize(400f, 300f, out) // scale clamped to 4
        assertTrue(out.all { it.isFinite() })
        val nan = ScreenGeometry.transform(floatArrayOf(0f, 0f, 100f, 75f), 1000f, 500f, Float.NaN, Float.NaN, Float.NaN)
        assertArrayEquals(floatArrayOf(0f, 0f, 100f, 75f), nan, 0.001f)
    }

    @Test fun offsetsForRoundTripsThroughTransform() {
        val base = floatArrayOf(100f, 75f, 400f, 300f)
        for (sc in listOf(0.5f, 1f, 1.7f)) for (tx in listOf(0f, 120f, 380f)) for (ty in listOf(0f, 90f, 260f)) {
            val o = ScreenGeometry.offsetsFor(base, 800f, 600f, sc, tx, ty)
            val r = ScreenGeometry.transform(base, 800f, 600f, sc, o[0], o[1])
            assertEquals(tx, r[0], 0.01f); assertEquals(ty, r[1], 0.01f)
            assertEquals(400f * sc, r[2], 0.01f); assertEquals(300f * sc, r[3], 0.01f)
        }
    }

    @Test fun offsetsForCentresRect() {
        val base = floatArrayOf(40f, 30f, 400f, 300f)
        val sc = 1.5f
        val o = ScreenGeometry.offsetsFor(base, 800f, 600f, sc, (800f - 400f * sc) / 2f, (600f - 300f * sc) / 2f)
        val r = ScreenGeometry.transform(base, 800f, 600f, sc, o[0], o[1])
        assertEquals(400f, r[0] + r[2] / 2f, 0.01f) // centre of rect == centre of view
        assertEquals(300f, r[1] + r[3] / 2f, 0.01f)
    }

    @Test fun clampInsideLeavesLegalValuesUntouched() {
        val base = floatArrayOf(100f, 75f, 400f, 300f)
        val out = ScreenGeometry.clampInside(base, 800f, 600f, 1f, 0.05f, -0.05f)
        assertArrayEquals(floatArrayOf(1f, 0.05f, -0.05f), out, 0.001f)
    }

    @Test fun clampInsideCapsScaleAtFit() {
        // Centred half-size rect in a 1000x600 view fits at most at scale 2,
        // filling the view exactly, so any offset must clamp to zero.
        val base = floatArrayOf(250f, 150f, 500f, 300f)
        val out = ScreenGeometry.clampInside(base, 1000f, 600f, 3f, 0.4f, -0.4f)
        assertArrayEquals(floatArrayOf(2f, 0f, 0f), out, 0.001f)
    }

    @Test fun clampInsidePullsOffsetsBackToEdges() {
        val base = floatArrayOf(100f, 75f, 400f, 300f)
        val out = ScreenGeometry.clampInside(base, 800f, 600f, 1f, 1f, -1f)
        val r = ScreenGeometry.transform(base, 800f, 600f, out[0], out[1], out[2])
        assertEquals(800f, r[0] + r[2], 0.01f) // right edge exactly on screen
        assertEquals(0f, r[1], 0.01f) // top edge exactly on screen
    }

    @Test fun clampInsideResultIsAlwaysInside() {
        val bases = listOf(
            floatArrayOf(0f, 0f, 800f, 600f),
            floatArrayOf(100f, 75f, 400f, 300f),
            floatArrayOf(600f, 400f, 200f, 150f),
        )
        for (b in bases) for (sc in listOf(0.25f, 1f, 2f, 4f)) for (o in listOf(-1f, 0f, 1f)) {
            val c = ScreenGeometry.clampInside(b, 800f, 600f, sc, o, -o)
            val r = ScreenGeometry.transform(b, 800f, 600f, c[0], c[1], c[2])
            assertTrue(r[0] >= -0.01f && r[1] >= -0.01f)
            assertTrue(r[0] + r[2] <= 800.01f && r[1] + r[3] <= 600.01f)
        }
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
