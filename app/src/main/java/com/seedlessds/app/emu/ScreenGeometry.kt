package com.seedlessds.app.emu

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** Pixel geometry shared by both displays. No Android dependencies. */
object ScreenGeometry {
    fun fitted(width: Float, height: Float, keepRatio: Boolean, integer: Boolean): Pair<Float, Float> {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return 0f to 0f
        if (!keepRatio && !integer) return width to height
        var scale = min(width / 256f, height / 192f)
        // A window smaller than one DS pixel grid must downscale rather than crop.
        if (integer && scale >= 1f) scale = floor(scale)
        return 256f * scale to 192f * scale
    }

    private fun density(panel: FloatArray): Float? {
        val x = panel.getOrNull(2) ?: 0f
        val y = panel.getOrNull(3) ?: 0f
        if (x.isFinite() && y.isFinite() && x in 40f..2000f && y in 40f..2000f)
            return sqrt(x * y)
        return panel.getOrNull(4)?.takeIf { it.isFinite() && it in 40f..2000f }
    }

    fun rect(width: Float, height: Float, keepRatio: Boolean, integer: Boolean,
             fillW: Boolean, fillH: Boolean, align: Int, sameSize: Boolean = false,
             own: FloatArray? = null, other: FloatArray? = null): FloatArray {
        var (w, h) = fitted(width, height, keepRatio, integer)
        if (w <= 0f || h <= 0f) return floatArrayOf(0f, 0f, 0f, 0f)
        if (sameSize && other != null && other.size >= 2 &&
            other[0].isFinite() && other[1].isFinite() && other[0] > 0f && other[1] > 0f) {
            val mine = own?.let(::density)
            val theirs = density(other)
            // If either density is unknown, compare pixels on BOTH sides.
            val a = if (mine != null && theirs != null) mine else 1f
            val b = if (mine != null && theirs != null) theirs else 1f
            val commonW = min(width / a, other[0] / b)
            val commonH = min(height / a, other[1] / b)
            val shared = fitted(commonW, commonH, keepRatio || integer, false)
            w = shared.first * a
            h = shared.second * a
            // Integer pixels take priority over exact physical matching.
            if (integer) {
                val snapped = fitted(w, h, true, true)
                w = snapped.first; h = snapped.second
            }
        } else if (!integer && keepRatio) {
            // Explicit stretching remains available, but never defeats integer scaling.
            if (fillW) w = width
            if (fillH) h = height
        }
        val x = ((width - w) / 2f).coerceAtLeast(0f)
        val y = when (align) { 0 -> 0f; 2 -> (height - h).coerceAtLeast(0f); else -> ((height - h) / 2f).coerceAtLeast(0f) }
        return floatArrayOf(x, y, w, h)
    }

    fun externalScreen(screen: Int, swapped: Boolean): Int = (screen xor if (swapped) 1 else 0) and 1
}
