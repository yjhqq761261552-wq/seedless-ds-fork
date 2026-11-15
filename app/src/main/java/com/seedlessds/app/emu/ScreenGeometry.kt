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

    /**
     * Per-screen free transform shared by every display path: scale [r] about
     * the centre of its view, then shift by [offX]/[offY] as fractions of the
     * view size. Identity (scale 1, offsets 0) returns [r] unchanged, so a
     * screen the user never adjusted renders exactly as before. Scale and
     * offsets are clamped so a stored value can never push a picture fully
     * out of reach.
     */
    fun transform(r: FloatArray, viewW: Float, viewH: Float, scale: Float, offX: Float, offY: Float): FloatArray {
        if (r.size < 4 || !viewW.isFinite() || !viewH.isFinite() || viewW <= 0f || viewH <= 0f) return r.copyOf()
        if (!r.all { it.isFinite() }) return r.copyOf()
        val sc = if (scale.isFinite()) scale.coerceIn(0.05f, 4f) else 1f
        val ox = if (offX.isFinite()) offX.coerceIn(-1f, 1f) else 0f
        val oy = if (offY.isFinite()) offY.coerceIn(-1f, 1f) else 0f
        if (sc == 1f && ox == 0f && oy == 0f) return r.copyOf()
        val cx = viewW / 2f; val cy = viewH / 2f
        val nx = cx + (r[0] - cx) * sc + ox * viewW
        val ny = cy + (r[1] - cy) * sc + oy * viewH
        return floatArrayOf(nx, ny, r[2] * sc, r[3] * sc)
    }

    /**
     * Inverse of [transform] for position: the (offX, offY) that puts the
     * transformed rect's top-left corner at ([targetX], [targetY]) for the
     * given [scale]. Unclamped — pass the result through [clampInside] when
     * the rect must stay in the view. Used by the layout editor for centre
     * and snap-to-neighbour operations and for pixel-exact positioning.
     */
    fun offsetsFor(r: FloatArray, viewW: Float, viewH: Float, scale: Float, targetX: Float, targetY: Float): FloatArray {
        if (r.size < 4 || !viewW.isFinite() || !viewH.isFinite() || viewW <= 0f || viewH <= 0f) return floatArrayOf(0f, 0f)
        if (!r.all { it.isFinite() } || !targetX.isFinite() || !targetY.isFinite()) return floatArrayOf(0f, 0f)
        val sc = if (scale.isFinite()) scale.coerceIn(0.05f, 4f) else 1f
        val cx = viewW / 2f; val cy = viewH / 2f
        val ox = (targetX - (cx + (r[0] - cx) * sc)) / viewW
        val oy = (targetY - (cy + (r[1] - cy) * sc)) / viewH
        return floatArrayOf(ox, oy)
    }

    /**
     * Clamp a (scale, offX, offY) triple for [transform] so the transformed
     * rect stays fully inside the view: the scale is capped at the largest
     * value that still fits, and each offset is limited to the range that
     * keeps the corresponding edges on screen. Returns [scale, offX, offY].
     * Used by the layout editor, where a screen must never be pushed or
     * scaled partly out of its panel; the game rendering path itself keeps
     * using the raw stored values via [transform].
     */
    fun clampInside(r: FloatArray, viewW: Float, viewH: Float, scale: Float, offX: Float, offY: Float): FloatArray {
        val fallback = floatArrayOf(
            if (scale.isFinite()) scale.coerceIn(0.05f, 4f) else 1f,
            if (offX.isFinite()) offX.coerceIn(-1f, 1f) else 0f,
            if (offY.isFinite()) offY.coerceIn(-1f, 1f) else 0f,
        )
        if (r.size < 4 || !viewW.isFinite() || !viewH.isFinite() || viewW <= 0f || viewH <= 0f) return fallback
        if (!r.all { it.isFinite() } || r[2] <= 0f || r[3] <= 0f) return fallback
        val maxScale = min(viewW / r[2], viewH / r[3])
        if (maxScale <= 0f) return fallback
        val cx = viewW / 2f; val cy = viewH / 2f
        // Offset ranges that keep the rect inside at a given scale. transform()
        // additionally clamps offsets to [-1, 1], so a fit that would need more
        // offset than that is only reachable by shrinking the scale further.
        fun rangeX(sc: Float): Pair<Float, Float> {
            val bx = cx + (r[0] - cx) * sc
            return maxOf(-bx / viewW, -1f) to minOf((viewW - r[2] * sc - bx) / viewW, 1f)
        }
        fun rangeY(sc: Float): Pair<Float, Float> {
            val by = cy + (r[1] - cy) * sc
            return maxOf(-by / viewH, -1f) to minOf((viewH - r[3] * sc - by) / viewH, 1f)
        }
        var sc = fallback[0].coerceAtMost(maxScale)
        var guard = 0
        while ((rangeX(sc).first > rangeX(sc).second || rangeY(sc).first > rangeY(sc).second) && sc > 0.05f && guard < 80) {
            sc = maxOf(0.05f, sc * 0.9f); guard++
        }
        val (minOx, maxOx) = rangeX(sc)
        val (minOy, maxOy) = rangeY(sc)
        val ox = if (maxOx >= minOx) fallback[1].coerceIn(minOx, maxOx) else (minOx + maxOx) / 2f
        val oy = if (maxOy >= minOy) fallback[2].coerceIn(minOy, maxOy) else (minOy + maxOy) / 2f
        return floatArrayOf(sc, ox, oy)
    }
}
