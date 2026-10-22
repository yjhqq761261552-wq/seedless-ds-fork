package com.seedlessds.app.emu

import android.view.Surface
import androidx.compose.runtime.mutableStateOf

object VulkanNative {
    init { System.loadLibrary("recon") }
    external fun create(surface: Surface, width: Int, height: Int, scaleCeiling: Int, mode: Int, external: Boolean = false, lowLatency: Boolean = false): Long
    external fun draw(handle: Long, geometry: FloatArray, bottomFirst: Boolean): Int
    external fun awaitFrame(handle: Long)
    external fun wake()
    external fun destroy(handle: Long)
}

object DisplayBackendStatus {
    val vulkan = mutableStateOf(false)
    val fallback = mutableStateOf<VulkanPolicy.Fallback?>(null)
}
