package com.seedlessds.app.emu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Per-screen decorative mask images, modelled on WatermelonDS's per-screen
 * layout backgrounds: the top and bottom DS screens each have their own
 * image, and single-surface layouts (both screens on one panel) use a third
 * one. A mask is drawn over the whole panel with the actual game screen
 * rectangles punched out, so it frames the picture without ever covering it.
 * Images are copied into app storage on import; the settings only keep the
 * slot's file name ("" = no mask).
 */
object ScreenMasks {
    const val MODE_STRETCH = 0
    const val MODE_FIT_CENTER = 1
    const val MODE_FIT_TOP = 2
    const val MODE_FIT_LEFT = 3
    const val MODE_FIT_BOTTOM = 4
    const val MODE_FIT_RIGHT = 5
    const val MODE_COUNT = 6

    enum class Slot(val fileName: String) {
        TOP("top.img"),
        BOTTOM("bottom.img"),
        SINGLE("single.img"),
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "masks").apply { mkdirs() }

    fun file(ctx: Context, slot: Slot): File = File(dir(ctx), slot.fileName)

    /** Copy the picked image into the slot's file. Returns false when the
     *  source cannot be read or does not decode as an image; on failure any
     *  previous mask for the slot is left untouched. */
    fun importImage(ctx: Context, slot: Slot, uri: Uri): Boolean {
        val tmp = File(dir(ctx), slot.fileName + ".tmp")
        val copied = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return false
            val bounds = imageBounds(tmp)
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)
        if (!copied) {
            tmp.delete()
            return false
        }
        if (!tmp.renameTo(file(ctx, slot))) {
            tmp.delete()
            return false
        }
        return true
    }

    private fun imageBounds(file: File): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        return bounds
    }

    fun clear(ctx: Context, slot: Slot) {
        runCatching { file(ctx, slot).delete() }
    }

    /** Decode the slot's image sampled down so neither dimension exceeds
     *  roughly twice the panel it will cover — full-resolution camera photos
     *  would otherwise be decoded whole just to be drawn once. */
    /** Which slot a surface shows: both screens -> the single-screen mask,
     *  otherwise the mask of the one screen on display. Null when neither. */
    fun slotFor(topShown: Boolean, bottomShown: Boolean): Slot? = when {
        topShown && bottomShown -> Slot.SINGLE
        topShown -> Slot.TOP
        bottomShown -> Slot.BOTTOM
        else -> null
    }

    /** A decoded mask plus its destination rect in surface pixels. [pixels]
     *  is the bitmap in direct-buffer form for the Vulkan presenter (BGRA
     *  byte order, matching VK_FORMAT_B8G8R8A8_UNORM). */
    class Background(val bitmap: Bitmap, val rect: FloatArray) {
        val pixels: java.nio.ByteBuffer by lazy {
            val buf = java.nio.ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(buf)
            buf.position(0)
            buf
        }
    }

    fun background(ctx: Context, s: DsSettings, slot: Slot, viewW: Int, viewH: Int): Background? {
        val bmp = decode(ctx, slot, viewW, viewH) ?: return null
        val r = destRect(s.maskMode(slot), bmp.width.toFloat(), bmp.height.toFloat(),
            viewW.toFloat(), viewH.toFloat())
        return Background(bmp, r)
    }

    fun decode(ctx: Context, slot: Slot, viewW: Int, viewH: Int): Bitmap? {
        val f = file(ctx, slot)
        if (!f.isFile || viewW <= 0 || viewH <= 0) return null
        return runCatching {
            val bounds = imageBounds(f)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= viewW || bounds.outHeight / (sample * 2) >= viewH) {
                sample *= 2
            }
            f.inputStream().use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        }.getOrNull()
    }

    /** Where the mask image lands on a view of [viewW]x[viewH], as
     *  [x, y, w, h] in pixels. Mirrors WatermelonDS's BackgroundMode set. */
    fun destRect(mode: Int, imgW: Float, imgH: Float, viewW: Float, viewH: Float): FloatArray {
        if (imgW <= 0f || imgH <= 0f || viewW <= 0f || viewH <= 0f) return floatArrayOf(0f, 0f, 0f, 0f)
        if (mode == MODE_STRETCH) return floatArrayOf(0f, 0f, viewW, viewH)
        val scale = minOf(viewW / imgW, viewH / imgH)
        val w = imgW * scale
        val h = imgH * scale
        val x = when (mode) {
            MODE_FIT_LEFT -> 0f
            MODE_FIT_RIGHT -> viewW - w
            else -> (viewW - w) / 2f
        }
        val y = when (mode) {
            MODE_FIT_TOP -> 0f
            MODE_FIT_BOTTOM -> viewH - h
            else -> (viewH - h) / 2f
        }
        return floatArrayOf(x, y, w, h)
    }

}

fun DsSettings.maskFile(slot: ScreenMasks.Slot): String = when (slot) {
    ScreenMasks.Slot.TOP -> maskTop
    ScreenMasks.Slot.BOTTOM -> maskBottom
    ScreenMasks.Slot.SINGLE -> maskSingle
}

fun DsSettings.maskMode(slot: ScreenMasks.Slot): Int = when (slot) {
    ScreenMasks.Slot.TOP -> maskTopMode
    ScreenMasks.Slot.BOTTOM -> maskBottomMode
    ScreenMasks.Slot.SINGLE -> maskSingleMode
}

fun DsSettings.withMask(slot: ScreenMasks.Slot, file: String, mode: Int): DsSettings = when (slot) {
    ScreenMasks.Slot.TOP -> copy(maskTop = file, maskTopMode = mode)
    ScreenMasks.Slot.BOTTOM -> copy(maskBottom = file, maskBottomMode = mode)
    ScreenMasks.Slot.SINGLE -> copy(maskSingle = file, maskSingleMode = mode)
}


/** Caches the current [ScreenMasks.Background] for one surface; recomputes
 *  only when the slot, mode, file or surface size changes. Render-thread
 *  confined (one instance per surface/driver). */
class MaskBackgrounds(private val ctx: Context) {
    private var key: String? = null
    private var cached: ScreenMasks.Background? = null

    fun current(s: DsSettings, slot: ScreenMasks.Slot?, viewW: Int, viewH: Int): ScreenMasks.Background? {
        if (slot == null || viewW <= 0 || viewH <= 0) {
            key = null
            cached = null
            return null
        }
        val f = ScreenMasks.file(ctx, slot)
        val k = slot.name + '|' + s.maskMode(slot) + '|' +
            (if (f.isFile) f.lastModified() else -1L) + '|' + f.length() + '|' + viewW + 'x' + viewH
        if (k != key) {
            key = k
            cached = ScreenMasks.background(ctx, s, slot, viewW, viewH)
        }
        return cached
    }
}
