package com.seedlessds.app.emu

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import kotlin.math.sqrt

object DeviceProfiles {
    class Panel(val w: Int, val h: Int, val diagonalInches: Float) {
        val dpi: Float get() = (sqrt((w.toDouble() * w + h.toDouble() * h)) / diagonalInches).toFloat()
        fun matches(pw: Int, ph: Int): Boolean = (pw == w && ph == h) || (pw == h && ph == w)
    }

    class Profile(val name: String, val manufacturer: String, val model: String, val device: String, val panels: List<Panel>)

    val profiles = listOf(
        Profile(
            "Retroid Pocket Duo Lite", "Moorechip", "Retroid Pocket Duo Lite", "trinket",
            listOf(Panel(1080, 1920, 5.5f), Panel(1280, 960, 4.2f)),
        ),
    )

    @Volatile private var resolved: Profile? = null

    fun pixelSize(d: Display): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 23) {
            val m = d.mode
            return m.physicalWidth to m.physicalHeight
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") d.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    fun current(ctx: Context): Profile? {
        resolved?.let { return it }
        val candidate = profiles.firstOrNull {
            it.manufacturer == Build.MANUFACTURER && it.model == Build.MODEL && it.device == Build.DEVICE
        } ?: return null
        val dm = ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
        val sizes = dm.displays.map { pixelSize(it) }
        val complete = candidate.panels.all { p -> sizes.any { (w, h) -> p.matches(w, h) } }
        if (!complete) return null
        resolved = candidate
        return candidate
    }

    fun dpiFor(ctx: Context, display: Display?): Float? {
        val profile = current(ctx) ?: return null
        val (w, h) = pixelSize(display ?: return null)
        return profile.panels.firstOrNull { it.matches(w, h) }?.dpi
    }
}
