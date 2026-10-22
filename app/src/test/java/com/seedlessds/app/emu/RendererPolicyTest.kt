package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test

class RendererPolicyTest {
    @Test fun vulkanHasOnlyImplementedBuiltInFilters() {
        assertEquals(listOf("None", "Linear", "FXAA", "lcd1x", "lcd1x+nds_color", "zfast_lcd", "zfast_lcd+nds_color", "sharp_bilinear", "sharp_bilinear+nds_color", "sharp_shimmerless", "sharp_shimmerless+nds_color", "pixel_aa_fast", "LCD3x", "LCD3x+nds_color", "Quilez", "Quilez+nds_color"), RendererPolicy.availableFilters(RendererPolicy.VULKAN, listOf("SMAA", "Custom")))
        assertEquals(RendererPolicy.vulkanFilters, RendererPolicy.availableFilters(RendererPolicy.VULKAN, emptyList()))
    }
    @Test fun openGlRetainsAllInstalledAndCustomShaders() {
        val installed = listOf("None", "Linear", "FXAA HQ", "SMAA", "Custom")
        assertEquals(installed, RendererPolicy.availableFilters(RendererPolicy.OPENGL, installed))
    }
    @Test fun switchingRendererKeepsCompatibleFilterAndLatencyPreference() {
        for (filter in RendererPolicy.vulkanFilters) {
            val before = DsSettings(filter = filter, vulkanLowLatency = true)
            val switched = RendererPolicy.select(before, RendererPolicy.VULKAN)
            assertEquals(filter, switched.filter)
            assertTrue(switched.vulkanLowLatency)
            assertEquals(before, RendererPolicy.select(switched, RendererPolicy.OPENGL))
        }
    }
    @Test fun switchingFromCustomGlShaderSelectsVulkanNone() {
        val value = RendererPolicy.select(DsSettings(filter = "Custom"), RendererPolicy.VULKAN)
        assertEquals(RendererPolicy.VULKAN, value.renderer)
        assertEquals("None", value.filter)
    }
    @Test fun unknownRendererSafelyDefaultsToOpenGlWithoutChangingShader() {
        val value = RendererPolicy.normalize(DsSettings(renderer = "bad", filter = "Custom"))
        assertEquals(RendererPolicy.OPENGL, value.renderer)
        assertEquals("Custom", value.filter)
    }
    @Test fun nativeFilterIdsStayStableAcrossDisplays() {
        val filters = listOf("None", "Linear", "FXAA", "lcd1x", "lcd1x+nds_color", "zfast_lcd", "zfast_lcd+nds_color", "sharp_bilinear", "sharp_bilinear+nds_color", "sharp_shimmerless", "sharp_shimmerless+nds_color", "pixel_aa_fast", "LCD3x", "LCD3x+nds_color", "Quilez", "Quilez+nds_color")
        filters.forEachIndexed { index, filter -> assertEquals(index, RendererPolicy.vulkanFilterMode(filter)) }
        assertEquals(0, RendererPolicy.vulkanFilterMode("Custom"))
    }
    @Test fun lcdEffectsRemainVulkanWithEitherLatencyMode() {
        for (filter in RendererPolicy.vulkanFilters.drop(3)) for (latency in listOf(true, false)) {
            val value = DsSettings(renderer = RendererPolicy.VULKAN, filter = filter, vulkanLowLatency = latency)
            assertEquals(value, RendererPolicy.normalize(value))
            assertNull(VulkanPolicy.fallback(value, 35))
        }
    }

    @Test fun newFiltersDoNotDependOnInstalledOpenGlRecipes() {
        val selected = RendererPolicy.availableFilters(RendererPolicy.VULKAN, emptyList())
        for (name in listOf("sharp_shimmerless", "pixel_aa_fast", "LCD3x+nds_color", "Quilez+nds_color")) assertTrue(name, name in selected)
        assertFalse("SMAA" in selected)
    }
    @Test fun selectingGlPreservesTheCompleteCustomList() {
        val values = listOf("Custom", "SMAA", "LCD3x", "Quilez")
        assertEquals(values, RendererPolicy.availableFilters(RendererPolicy.OPENGL, values))
    }

    @Test fun switchingToGlResetsUnavailableNativeEffects() {
        val gl = listOf("None", "FXAA", "sharp_bilinear", "Custom")
        val vk = DsSettings(renderer = RendererPolicy.VULKAN, filter = "sharp_shimmerless")
        val result = RendererPolicy.select(vk, RendererPolicy.OPENGL, gl)
        assertEquals(RendererPolicy.OPENGL, result.renderer)
        assertEquals("None", result.filter)
        assertEquals("sharp_bilinear", RendererPolicy.select(vk.copy(filter = "sharp_bilinear"), RendererPolicy.OPENGL, gl).filter)
    }
    @Test fun inheritedNativeFilterCannotLeakIntoGlOverride() {
        val invalid = DsSettings(renderer = RendererPolicy.OPENGL, filter = "pixel_aa_fast")
        assertEquals("None", RendererPolicy.normalize(invalid, listOf("None", "Custom")).filter)
        // A real custom GLSL implementation with the same name remains valid.
        assertEquals(invalid, RendererPolicy.normalize(invalid, listOf("None", "pixel_aa_fast")))
    }

}
