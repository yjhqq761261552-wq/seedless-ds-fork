package com.seedlessds.app.emu

/** Renderer selection is independent of presentation latency and shader files.
 * Vulkan uses built-in effects; OpenGL owns the editable GLSL filter directory. */
object RendererPolicy {
    const val OPENGL = "OpenGL"
    const val VULKAN = "Vulkan"
    val vulkanFilters = listOf("None", "Linear", "FXAA", "lcd1x", "lcd1x+nds_color", "zfast_lcd", "zfast_lcd+nds_color", "sharp_bilinear", "sharp_bilinear+nds_color", "sharp_shimmerless", "sharp_shimmerless+nds_color", "pixel_aa_fast", "LCD3x", "LCD3x+nds_color", "Quilez", "Quilez+nds_color")

    /** IDs shared with the native shader; append new modes without reordering. */
    fun vulkanFilterMode(filter: String): Int = vulkanFilters.indexOf(filter).coerceAtLeast(0)

    fun usesVulkan(settings: DsSettings) = settings.renderer == VULKAN

    fun availableFilters(renderer: String, openGlFilters: List<String>): List<String> =
        if (renderer == VULKAN) vulkanFilters else openGlFilters

    fun normalize(settings: DsSettings, openGlFilters: List<String>? = null): DsSettings {
        val renderer = if (usesVulkan(settings)) VULKAN else OPENGL
        val compatible = if (renderer == VULKAN) settings.filter in vulkanFilters
            else openGlFilters == null || settings.filter in openGlFilters
        val filter = if (compatible) settings.filter else "None"
        return if (renderer == settings.renderer && filter == settings.filter) settings
        else settings.copy(renderer = renderer, filter = filter)
    }

    fun select(settings: DsSettings, renderer: String, openGlFilters: List<String>? = null) =
        normalize(settings.copy(renderer = renderer), openGlFilters)
}
