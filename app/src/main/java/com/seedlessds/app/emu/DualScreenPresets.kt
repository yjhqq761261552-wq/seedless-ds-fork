package com.seedlessds.app.emu

import com.seedlessds.app.R

object DualScreenPresets {
    const val OFF = 0
    const val INTERNAL_TOP = 1
    const val INTERNAL_BOTTOM = 2

    const val LAYOUT_FULLSCREEN = 4
    const val EXT_FULLSCREEN = 2

    const val ALIGN_TOP = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_BOTTOM = 2

    fun labels(uiContext: android.content.Context) = listOf(uiContext.getString(R.string.presets_disabled_manual_layout), uiContext.getString(R.string.presets_top_here_bottom_on_tv), uiContext.getString(R.string.presets_bottom_here_top_on_tv))
    fun alignLabels(uiContext: android.content.Context) = listOf(uiContext.getString(R.string.common_top), uiContext.getString(R.string.presets_centre), uiContext.getString(R.string.common_bottom))


    @Volatile var panelInternal: FloatArray? = null
    @Volatile var panelExternal: FloatArray? = null


    @Volatile var version = 0

    fun panel(w: Int, h: Int, xdpi: Float, ydpi: Float, densityDpi: Int): FloatArray =
        floatArrayOf(w.toFloat(), h.toFloat(), xdpi, ydpi, densityDpi.toFloat())

    @Synchronized fun publishInternal(p: FloatArray) {
        if (panelInternal?.contentEquals(p) == true) return
        panelInternal = p; version++
    }

    @Synchronized fun publishExternal(p: FloatArray) {
        if (panelExternal?.contentEquals(p) == true) return
        panelExternal = p; version++
    }


    fun live(s: DsSettings): Boolean = s.dualScreenPreset != OFF && s.extDisplayType != 0 && ExternalSurface.attached && ExternalSurface.active

    fun singleScreen(s: DsSettings): Boolean = if (s.dualScreenPreset != OFF) live(s) else s.singleScreen

    fun configuredSingle(s: DsSettings): Boolean = s.dualScreenPreset != OFF || s.singleScreen

    fun singleBottom(s: DsSettings): Boolean = if (s.dualScreenPreset != OFF) s.dualScreenPreset == INTERNAL_BOTTOM else s.singleBottom

    fun apply(s: DsSettings, preset: Int): DsSettings {
        if (preset == OFF) return s.copy(dualScreenPreset = OFF)
        return s.copy(
            dualScreenPreset = preset,
            singleScreen = true,
            singleBottom = preset == INTERNAL_BOTTOM,
            screenLayout = LAYOUT_FULLSCREEN,
            swapScreens = false,
            extDisplayType = EXT_FULLSCREEN,
            extDisplayScreen = if (preset == INTERNAL_TOP) 1 else 0,
        )
    }


    fun rect(
        viewW: Float, viewH: Float, keepRatio: Boolean, integer: Boolean,
        fillW: Boolean, fillH: Boolean, align: Int, sameSize: Boolean = false,
        own: FloatArray? = null, other: FloatArray? = null,
    ): FloatArray = ScreenGeometry.rect(viewW, viewH, keepRatio, integer,
        fillW, fillH, align, sameSize, own, other)

    @Synchronized fun clearExternal() {
        panelExternal = null
        version++
    }
}
