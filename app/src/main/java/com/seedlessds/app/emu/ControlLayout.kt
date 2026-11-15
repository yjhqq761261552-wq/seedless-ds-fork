package com.seedlessds.app.emu

import android.content.Context

object ControlLayout {
    private const val PREFS = "ctrl_layout"
    val ids = listOf("dpad", "face", "lbtn", "rbtn", "startselect", "menu", "extra")

    private val offsets = HashMap<String, Pair<Float, Float>>()
    private val scales = HashMap<String, Float>()
    private val hidden = HashSet<String>()

    @Volatile var topScale: Float = 1f
    @Volatile var topOffX: Float = 0f
    @Volatile var topOffY: Float = 0f
    @Volatile var botScale: Float = 1f
    @Volatile var botOffX: Float = 0f
    @Volatile var botOffY: Float = 0f
    /** Independent transform of the picture on the external (second physical) panel. */
    @Volatile var extScale: Float = 1f
    @Volatile var extOffX: Float = 0f
    @Volatile var extOffY: Float = 0f
    /**
     * Independent transform of the picture on THIS device's panel while an
     * external display is active (dual-screen mode). Kept separate from the
     * top/bot single-surface transforms so tuning one mode never disturbs the
     * other; geometry switches to it automatically when an external display
     * is attached and back when it is removed.
     */
    @Volatile var dualScale: Float = 1f
    @Volatile var dualOffX: Float = 0f
    @Volatile var dualOffY: Float = 0f
    @Volatile var backgroundUri: String = ""

    fun isHidden(id: String): Boolean = id in hidden
    fun setHidden(id: String, h: Boolean) { if (h) hidden.add(id) else hidden.remove(id) }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, 0)
        offsets.clear(); scales.clear()
        for (id in ids) {
            offsets[id] = p.getFloat("${id}_x", 0f) to p.getFloat("${id}_y", 0f)
            scales[id] = p.getFloat("${id}_s", 1f)
        }
        val oldScale = p.getFloat("screen_scale", 1f)
        val oldOffX = p.getFloat("screen_offx", 0f)
        val oldOffY = p.getFloat("screen_offy", 0f)
        topScale = p.getFloat("top_scale", oldScale)
        topOffX = p.getFloat("top_offx", oldOffX)
        topOffY = p.getFloat("top_offy", oldOffY)
        botScale = p.getFloat("bot_scale", oldScale)
        botOffX = p.getFloat("bot_offx", oldOffX)
        botOffY = p.getFloat("bot_offy", oldOffY)
        extScale = p.getFloat("ext_scale", 1f)
        extOffX = p.getFloat("ext_offx", 0f)
        extOffY = p.getFloat("ext_offy", 0f)
        dualScale = p.getFloat("dual_scale", 1f)
        dualOffX = p.getFloat("dual_offx", 0f)
        dualOffY = p.getFloat("dual_offy", 0f)
        hidden.clear(); p.getStringSet("hidden", null)?.let { hidden.addAll(it) }
        backgroundUri = p.getString("background_uri", "") ?: ""
    }

    fun offsetOf(id: String): Pair<Float, Float> = offsets[id] ?: (0f to 0f)
    fun scaleOf(id: String): Float = scales[id] ?: 1f

    fun save(
        ctx: Context,
        offsetValues: Map<String, Pair<Float, Float>>,
        scaleValues: Map<String, Float>,
    ) {
        val e = ctx.getSharedPreferences(PREFS, 0).edit()
        for ((id, off) in offsetValues) { e.putFloat("${id}_x", off.first); e.putFloat("${id}_y", off.second) }
        for ((id, s) in scaleValues) { e.putFloat("${id}_s", s) }
        e.putFloat("top_scale", topScale); e.putFloat("top_offx", topOffX); e.putFloat("top_offy", topOffY)
        e.putFloat("bot_scale", botScale); e.putFloat("bot_offx", botOffX); e.putFloat("bot_offy", botOffY)
        e.putFloat("ext_scale", extScale); e.putFloat("ext_offx", extOffX); e.putFloat("ext_offy", extOffY)
        e.putFloat("dual_scale", dualScale); e.putFloat("dual_offx", dualOffX); e.putFloat("dual_offy", dualOffY)
        e.putStringSet("hidden", HashSet(hidden))
        e.putFloat("screen_scale", topScale); e.putFloat("screen_offx", topOffX); e.putFloat("screen_offy", topOffY)
        e.apply()
        offsets.putAll(offsetValues); scales.putAll(scaleValues)
    }

    /** Persist only the screen transforms, leaving button layout untouched. */
    fun saveScreens(ctx: Context) {
        val e = ctx.getSharedPreferences(PREFS, 0).edit()
        e.putFloat("top_scale", topScale); e.putFloat("top_offx", topOffX); e.putFloat("top_offy", topOffY)
        e.putFloat("bot_scale", botScale); e.putFloat("bot_offx", botOffX); e.putFloat("bot_offy", botOffY)
        e.putFloat("ext_scale", extScale); e.putFloat("ext_offx", extOffX); e.putFloat("ext_offy", extOffY)
        e.putFloat("dual_scale", dualScale); e.putFloat("dual_offx", dualOffX); e.putFloat("dual_offy", dualOffY)
        e.putFloat("screen_scale", topScale); e.putFloat("screen_offx", topOffX); e.putFloat("screen_offy", topOffY)
        e.apply()
    }

    /** Reset only the screen transforms to identity, keeping button layout. */
    fun resetScreens(ctx: Context) {
        val e = ctx.getSharedPreferences(PREFS, 0).edit()
        for (k in listOf("top_scale", "top_offx", "top_offy", "bot_scale", "bot_offx", "bot_offy",
            "ext_scale", "ext_offx", "ext_offy", "dual_scale", "dual_offx", "dual_offy",
            "screen_scale", "screen_offx", "screen_offy")) e.remove(k)
        e.apply()
        topScale = 1f; topOffX = 0f; topOffY = 0f
        botScale = 1f; botOffX = 0f; botOffY = 0f
        extScale = 1f; extOffX = 0f; extOffY = 0f
        dualScale = 1f; dualOffX = 0f; dualOffY = 0f
    }

    fun setBackground(ctx: Context, uri: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("background_uri", uri).apply()
        backgroundUri = uri
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().clear().apply()
        offsets.clear(); scales.clear(); hidden.clear()
        for (id in ids) { offsets[id] = 0f to 0f; scales[id] = 1f }
        topScale = 1f; topOffX = 0f; topOffY = 0f
        botScale = 1f; botOffX = 0f; botOffY = 0f
        extScale = 1f; extOffX = 0f; extOffY = 0f
        dualScale = 1f; dualOffX = 0f; dualOffY = 0f
        backgroundUri = ""
    }
}
