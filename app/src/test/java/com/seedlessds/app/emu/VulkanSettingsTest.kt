package com.seedlessds.app.emu

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Actual SettingsRepo serialization with an in-memory implementation of the
 * Android preference interfaces; no Android framework calls or JNI needed. */
class VulkanSettingsTest {
    private class Preferences {
        val values = mutableMapOf<String, Any?>()
        val editor: SharedPreferences.Editor = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putBoolean", "putInt", "putFloat", "putLong", "putString" -> { values[args[0] as String] = args[1]; proxy }
                "remove" -> { values.remove(args[0] as String); proxy }
                "clear" -> { values.clear(); proxy }
                "apply" -> null
                "commit" -> true
                else -> error(method.name)
            }
        } as SharedPreferences.Editor
        val prefs: SharedPreferences = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean", "getInt", "getFloat", "getLong", "getString" -> values[args[0] as String] ?: args[1]
                "contains" -> values.containsKey(args[0] as String)
                "getAll" -> values.toMap()
                "edit" -> editor
                else -> error(method.name)
            }
        } as SharedPreferences
    }
    private fun write(p: Preferences, settings: DsSettings) {
        SettingsRepo::class.java.getDeclaredMethod("writeTo", SharedPreferences.Editor::class.java, DsSettings::class.java)
            .apply { isAccessible = true }.invoke(SettingsRepo, p.editor, settings)
    }
    private fun diff(p: Preferences, settings: DsSettings, global: DsSettings) {
        SettingsRepo::class.java.getDeclaredMethod("writeDiff", SharedPreferences.Editor::class.java, DsSettings::class.java, DsSettings::class.java)
            .apply { isAccessible = true }.invoke(SettingsRepo, p.editor, settings, global)
    }
    @Test fun oldConfigDefaultsToOpenGl() {
        assertFalse(SettingsRepo.readFrom(Preferences().prefs).vulkanLowLatency)
    }
    @Test fun globalToggleRoundTrips() {
        val p = Preferences()
        for (enabled in listOf(true, false, true)) {
            write(p, DsSettings(vulkanLowLatency = enabled))
            assertEquals(enabled, SettingsRepo.readFrom(p.prefs).vulkanLowLatency)
        }
    }
    @Test fun perGameConfigInheritsGlobal() {
        assertTrue(SettingsRepo.readFrom(Preferences().prefs, DsSettings(vulkanLowLatency = true)).vulkanLowLatency)
    }
    @Test fun perGameCanDisableGlobalMode() {
        val p = Preferences()
        val global = DsSettings(vulkanLowLatency = true)
        diff(p, global.copy(vulkanLowLatency = false), global)
        assertEquals(false, p.values["_VulkanLowLatency"])
        assertFalse(SettingsRepo.readFrom(p.prefs, global).vulkanLowLatency)
    }
    @Test fun perGameCanEnableModeAndClearOverride() {
        val p = Preferences()
        val global = DsSettings()
        diff(p, global.copy(vulkanLowLatency = true), global)
        assertTrue(SettingsRepo.readFrom(p.prefs, global).vulkanLowLatency)
        diff(p, global, global)
        assertFalse(p.values.containsKey("_VulkanLowLatency"))
    }
    @Test fun existingRewindAndInputPreferencesSurviveRoundTrip() {
        val p = Preferences()
        val config = DsSettings(vulkanLowLatency = true, rewindEnabled = true, rewindSeconds = 60,
            lowLatencyTouch = false, noDiagonals = true, analogDeadzone = .2f, filter = "SMAA")
        write(p, config)
        val restored = SettingsRepo.readFrom(p.prefs)
        assertTrue(restored.vulkanLowLatency)
        assertTrue(restored.rewindEnabled)
        assertEquals(60, restored.rewindSeconds)
        assertFalse(restored.lowLatencyTouch)
        assertTrue(restored.noDiagonals)
        assertEquals(.2f, restored.analogDeadzone, 0f)
        assertEquals("SMAA", restored.filter)
    }
    @Test fun rendererDefaultsToOpenGlForLegacyEnabledVulkanFlag() {
        val p = Preferences();p.values["_VulkanLowLatency"] = true
        assertEquals(RendererPolicy.OPENGL, SettingsRepo.readFrom(p.prefs).renderer)
    }
    @Test fun rendererAndLatencyRoundTripIndependently() {
        val p = Preferences()
        for (renderer in listOf(RendererPolicy.OPENGL, RendererPolicy.VULKAN)) for (latency in listOf(true, false)) {
            val value = DsSettings(renderer = renderer, vulkanLowLatency = latency, filter = "FXAA")
            write(p, value)
            assertEquals(value, SettingsRepo.readFrom(p.prefs))
        }
    }
    @Test fun perGameRendererOverridesAndInheritsGlobal() {
        val p = Preferences();val global = DsSettings(renderer = RendererPolicy.VULKAN, filter = "Linear")
        assertEquals(global, SettingsRepo.readFrom(p.prefs, global))
        val local = global.copy(renderer = RendererPolicy.OPENGL, filter = "SMAA")
        diff(p, local, global);assertEquals(local, SettingsRepo.readFrom(p.prefs, global))
        diff(p, global, global);assertFalse(p.values.containsKey("_Renderer"))
        assertEquals(global, SettingsRepo.readFrom(p.prefs, global))
    }
    @Test fun invalidPersistedVulkanFilterResetsInsteadOfFallback() {
        val p = Preferences();p.values["_Renderer"] = RendererPolicy.VULKAN;p.values["_CurrentFx"] = "SMAA"
        val value = SettingsRepo.readFrom(p.prefs)
        assertEquals(RendererPolicy.VULKAN, value.renderer)
        assertEquals("None", value.filter)
    }

    @Test fun builtInFiltersPersistInGlobalAndPerGameConfigurations() {
        for (filter in RendererPolicy.vulkanFilters.drop(3)) {
            val global = DsSettings(renderer = RendererPolicy.VULKAN, filter = filter)
            val p = Preferences();write(p, global)
            assertEquals(global, SettingsRepo.readFrom(p.prefs))
            val local = global.copy(vulkanLowLatency = true)
            val overrides = Preferences();diff(overrides, local, DsSettings())
            assertEquals(local, SettingsRepo.readFrom(overrides.prefs, DsSettings()))
        }
    }

    @Test fun interpolationPerGameOverrideClearsBackToGlobalFilter() {
        val global = DsSettings(renderer = RendererPolicy.VULKAN, filter = "FXAA")
        for (name in RendererPolicy.vulkanFilters.drop(7)) {
            val p = Preferences();val local = global.copy(filter = name)
            diff(p, local, global);assertEquals(local, SettingsRepo.readFrom(p.prefs, global))
            diff(p, global, global);assertFalse(p.values.containsKey("_CurrentFx"))
            assertEquals(global, SettingsRepo.readFrom(p.prefs, global))
        }
    }

}
