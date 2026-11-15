package com.seedlessds.app.emu

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

class ScreenMasksDestRectTest {
    @Test fun stretchFillsView() {
        val r = ScreenMasks.destRect(ScreenMasks.MODE_STRETCH, 100f, 50f, 1920f, 1080f)
        assertEquals(listOf(0f, 0f, 1920f, 1080f), r.toList())
    }

    @Test fun fitCenterLetterboxes() {
        // Square image into a wide view: height-limited, centred horizontally.
        val r = ScreenMasks.destRect(ScreenMasks.MODE_FIT_CENTER, 100f, 100f, 1600f, 900f)
        assertEquals(900f, r[2], 0.01f)
        assertEquals(900f, r[3], 0.01f)
        assertEquals(350f, r[0], 0.01f)
        assertEquals(0f, r[1], 0.01f)
    }

    @Test fun fitAnchors() {
        // 400x100 into 1000x1000: scale 2.5 -> 1000x250, vertical placement varies.
        val top = ScreenMasks.destRect(ScreenMasks.MODE_FIT_TOP, 400f, 100f, 1000f, 1000f)
        assertEquals(1000f, top[2], 0.01f)
        assertEquals(250f, top[3], 0.01f)
        assertEquals(0f, top[1], 0.01f)
        val bottom = ScreenMasks.destRect(ScreenMasks.MODE_FIT_BOTTOM, 400f, 100f, 1000f, 1000f)
        assertEquals(750f, bottom[1], 0.01f)
        // 100x400 into 1000x1000: scale 2.5 -> 250x1000, horizontal placement varies.
        val left = ScreenMasks.destRect(ScreenMasks.MODE_FIT_LEFT, 100f, 400f, 1000f, 1000f)
        assertEquals(0f, left[0], 0.01f)
        assertEquals(250f, left[2], 0.01f)
        val right = ScreenMasks.destRect(ScreenMasks.MODE_FIT_RIGHT, 100f, 400f, 1000f, 1000f)
        assertEquals(750f, right[0], 0.01f)
    }

    @Test fun degenerateInputsAreEmpty() {
        assertEquals(0f, ScreenMasks.destRect(ScreenMasks.MODE_FIT_CENTER, 0f, 10f, 100f, 100f)[2], 0.0f)
    }

    @Test fun slotSelection() {
        assertEquals(ScreenMasks.Slot.SINGLE, ScreenMasks.slotFor(topShown = true, bottomShown = true))
        assertEquals(ScreenMasks.Slot.TOP, ScreenMasks.slotFor(topShown = true, bottomShown = false))
        assertEquals(ScreenMasks.Slot.BOTTOM, ScreenMasks.slotFor(topShown = false, bottomShown = true))
        assertNull(ScreenMasks.slotFor(topShown = false, bottomShown = false))
    }

    @Test fun settingsMappingRoundTrips() {
        val s0 = DsSettings()
        assertEquals("", s0.maskFile(ScreenMasks.Slot.TOP))
        val s1 = s0.withMask(ScreenMasks.Slot.BOTTOM, "bottom.img", ScreenMasks.MODE_FIT_TOP)
        assertEquals("bottom.img", s1.maskFile(ScreenMasks.Slot.BOTTOM))
        assertEquals(ScreenMasks.MODE_FIT_TOP, s1.maskMode(ScreenMasks.Slot.BOTTOM))
        assertEquals("", s1.maskFile(ScreenMasks.Slot.SINGLE))
        assertEquals(ScreenMasks.MODE_STRETCH, s1.maskMode(ScreenMasks.Slot.TOP))
    }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ScreenMasksRobolectricTest {
    private val ctx get() = RuntimeEnvironment.getApplication()

    /** A real 4x4 PNG written to a temp file, returned as a file:// uri. */
    private fun pngUri(width: Int = 4, height: Int = 4): Uri {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        val f = File.createTempFile("mask-fixture-", ".png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        f.deleteOnExit()
        return Uri.fromFile(f)
    }

    @Test fun importAcceptsImageAndRejectsText() {
        assertTrue(ScreenMasks.importImage(ctx, ScreenMasks.Slot.TOP, pngUri()))
        assertTrue(ScreenMasks.file(ctx, ScreenMasks.Slot.TOP).isFile)
        assertNotNull(ScreenMasks.decode(ctx, ScreenMasks.Slot.TOP, 100, 100))

        val bad = File.createTempFile("mask-fixture-", ".txt")
        bad.writeText("not an image")
        bad.deleteOnExit()
        assertFalse(ScreenMasks.importImage(ctx, ScreenMasks.Slot.BOTTOM, Uri.fromFile(bad)))
        assertFalse(ScreenMasks.file(ctx, ScreenMasks.Slot.BOTTOM).isFile)

        ScreenMasks.clear(ctx, ScreenMasks.Slot.TOP)
        assertFalse(ScreenMasks.file(ctx, ScreenMasks.Slot.TOP).isFile)
    }

    @Test fun truncatedImageCannotReplaceExistingMask() {
        val slot = ScreenMasks.Slot.SINGLE
        assertTrue(ScreenMasks.importImage(ctx, slot, pngUri()))
        val saved = ScreenMasks.file(ctx, slot).readBytes()
        val invalid = File.createTempFile("mask-truncated-", ".png")
        invalid.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        invalid.deleteOnExit()
        assertFalse(ScreenMasks.importImage(ctx, slot, Uri.fromFile(invalid)))
        assertArrayEquals(saved, ScreenMasks.file(ctx, slot).readBytes())
        ScreenMasks.clear(ctx, slot)
    }

    @Test fun panoramicImageIsBoundedByPanelDimensions() {
        val slot = ScreenMasks.Slot.SINGLE
        assertTrue(ScreenMasks.importImage(ctx, slot, pngUri(1024, 16)))
        val decoded = ScreenMasks.decode(ctx, slot, 100, 100)
        assertNotNull(decoded)
        assertTrue(decoded!!.width <= 200)
        assertTrue(decoded.height <= 200)
        decoded.recycle()
        ScreenMasks.clear(ctx, slot)
    }

}
