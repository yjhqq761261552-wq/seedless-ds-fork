package com.seedlessds.app.emu

import android.view.Surface
import androidx.compose.runtime.mutableStateOf

object VulkanNative {
    init { System.loadLibrary("recon") }
    external fun create(surface: Surface, width: Int, height: Int, scaleCeiling: Int, mode: Int, external: Boolean = false, lowLatency: Boolean = false): Long
    external fun draw(handle: Long, geometry: FloatArray, bottomFirst: Boolean): Int
    /** Mask background drawn under the screens. [pixels] is a direct buffer of
     *  BGRA bytes (Android ARGB_8888 layout), [rect] the destination in
     *  surface pixels; null pixels clear the background. */
    external fun setBackground(handle: Long, pixels: java.nio.ByteBuffer?, width: Int, height: Int, rect: FloatArray)
    external fun awaitFrame(handle: Long)
    external fun wake()
    external fun destroy(handle: Long)
}

object DisplayBackendStatus {
    val vulkan = mutableStateOf(false)
    val fallback = mutableStateOf<VulkanPolicy.Fallback?>(null)
}
