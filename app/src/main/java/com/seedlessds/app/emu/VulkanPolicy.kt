package com.seedlessds.app.emu

/** Only genuine platform/driver constraints may fall back; filters never select the API. */
object VulkanPolicy {
    enum class Fallback { OFF, ANDROID, DIAGNOSTICS, DRIVER }

    fun fallback(settings: DsSettings, api: Int, diagnostics: Boolean = false): Fallback? = when {
        !RendererPolicy.usesVulkan(settings) -> Fallback.OFF
        api < 24 -> Fallback.ANDROID
        diagnostics -> Fallback.DIAGNOSTICS
        else -> null
    }
}
