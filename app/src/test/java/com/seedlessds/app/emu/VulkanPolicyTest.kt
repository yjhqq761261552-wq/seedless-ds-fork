package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test

class VulkanPolicyTest {
    private val enabled = DsSettings(renderer = RendererPolicy.VULKAN)
    @Test fun defaultRemainsOpenGlEvenWithLegacyLatencyFlag() {
        assertEquals(RendererPolicy.OPENGL, DsSettings().renderer)
        assertEquals(VulkanPolicy.Fallback.OFF, VulkanPolicy.fallback(DsSettings(vulkanLowLatency = true), 35))
    }
    @Test fun requiresAndroidSeven() {
        for (api in 21..23) assertEquals(VulkanPolicy.Fallback.ANDROID, VulkanPolicy.fallback(enabled, api))
        for (api in 24..35) assertNull(VulkanPolicy.fallback(enabled, api))
    }
    @Test fun latencySwitchNeverChangesRenderer() {
        for (latency in listOf(true, false)) assertNull(VulkanPolicy.fallback(enabled.copy(vulkanLowLatency = latency), 35))
    }
    @Test fun incompatibleFiltersNormalizeWithoutSwitchingToOpenGl() {
        for (name in listOf("SMAA", "FXAA HQ", "Custom", "")) {
            val normalized = RendererPolicy.normalize(enabled.copy(filter = name))
            assertEquals("None", normalized.filter)
            assertEquals(RendererPolicy.VULKAN, normalized.renderer)
            assertNull(VulkanPolicy.fallback(normalized, 35))
        }
    }
    @Test fun externalDelayCannotSilentlyChangeRenderer() {
        assertNull(VulkanPolicy.fallback(enabled.copy(extDisplayDelay = 2), 35))
    }
    @Test fun diagnosticsHasAnExplicitCompatibilityReason() {
        assertEquals(VulkanPolicy.Fallback.DIAGNOSTICS, VulkanPolicy.fallback(enabled, 35, diagnostics = true))
    }
}
