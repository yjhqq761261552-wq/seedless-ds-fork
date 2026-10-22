package com.seedlessds.app.emu

/** UI input aggregation, independent of Android and of the native frame loop. */
class LiveInput(private val publish: (Int, Int, Int) -> Unit) {
    companion object {
        const val VIRTUAL = 0
        const val KEYBOARD = 1
        const val JOYSTICK = 2
        const val PRIMARY_TOUCH = 0
        const val EXTERNAL_TOUCH = 1
        private const val SOURCES = 3
    }

    private val buttons = HashMap<Long, Int>()
    private var touch = 0
    private var touching = false
    private var touchOwner = -1
    private var lastTouchSource = -1
    private var autofire = 0
    private var lastPad = 0
    private var lastTouch = 0
    private var lastAutofire = 0
    private var published = false

    private fun pad(): Int = buttons.values.fold(0) { a, b -> a or b }

    // Keep publication inside this short lock so concurrent callbacks cannot
    // reorder newer and older snapshots. The callback only copies native input.
    private fun emit(force: Boolean = false) {
        val packedPad = pad() or if (touching) Int.MIN_VALUE else 0
        val packedTouch = if (touching) touch else 0
        if (!force && published && packedPad == lastPad && packedTouch == lastTouch && autofire == lastAutofire) return
        publish(packedPad, packedTouch, autofire)
        lastPad = packedPad; lastTouch = packedTouch; lastAutofire = autofire; published = true
    }

    @Synchronized fun setButtons(mask: Int, pressed: Int, source: Int = VIRTUAL, deviceId: Int = 0) {
        require(source in 0 until SOURCES)
        val safeMask = mask and Int.MAX_VALUE
        val key = (source.toLong() shl 32) or (deviceId.toLong() and 0xffffffffL)
        val next = ((buttons[key] ?: 0) and safeMask.inv()) or (pressed and safeMask)
        if (next == 0) buttons.remove(key) else buttons[key] = next
        emit()
    }

    fun setButton(index: Int, pressed: Boolean, source: Int = VIRTUAL, deviceId: Int = 0) {
        if (index !in 0..30) return
        val bit = 1 shl index
        setButtons(bit, if (pressed) bit else 0, source, deviceId)
    }

    @Synchronized fun setTouch(active: Boolean, packed: Int, source: Int = PRIMARY_TOUCH) {
        if (!active && touchOwner != -1 && touchOwner != source) return
        if (active) lastTouchSource = source
        touchOwner = if (active) source else -1
        touching = active; touch = if (active) packed else 0
        emit()
    }

    @Synchronized fun cancelTouch(source: Int): Boolean {
        if (touchOwner != source && (touchOwner != -1 || lastTouchSource != source)) return false
        touchOwner = -1; lastTouchSource = -1; touching = false; touch = 0
        emit(force = true)
        return true
    }

    @Synchronized fun toggleAutofire() {
        autofire = autofire xor (pad() and 0x3f0)
        emit()
    }

    @Synchronized fun releaseAll() {
        buttons.clear(); touching = false; touch = 0; touchOwner = -1; lastTouchSource = -1
        // Force a neutral publication even after native reset/load operations.
        emit(force = true)
    }

    @Synchronized fun releaseDevice(deviceId: Int) {
        buttons.keys.removeAll { (it ushr 32).toInt() != VIRTUAL && it.toInt() == deviceId }
        emit(force = true)
    }

    @Synchronized fun republish() = emit(force = true)
}

object DsTouchCoordinates {
    fun map(x: Float, y: Float, rect: FloatArray): Int? {
        if (rect.size < 4 || !x.isFinite() || !y.isFinite() ||
            rect.any { !it.isFinite() } || rect[2] <= 0f || rect[3] <= 0f) return null
        val u = (x - rect[0]) / rect[2]
        val v = (y - rect[1]) / rect[3]
        if (u !in 0f..1f || v !in 0f..1f) return null
        val dx = (u * 256f).toInt().coerceIn(0, 255)
        val dy = (v * 192f).toInt().coerceIn(0, 191)
        return (dx shl 16) or dy
    }
}

object StickInput {
    const val UP = 1
    const val DOWN = 2
    const val LEFT = 4
    const val RIGHT = 8
    // tan(22.5 degrees): equal-width cardinal/diagonal sectors in eight-way mode.
    private const val DIAGONAL_RATIO = 0.41421356f

    fun direction(value: Float, deadzone: Float): Int {
        if (!value.isFinite()) return 0
        val limit = if (deadzone.isFinite()) deadzone.coerceIn(0f, 1f) else 0.5f
        return when { value < -limit -> -1; value > limit -> 1; else -> 0 }
    }

    fun buttons(
        rawX: Float, rawY: Float, deadzone: Float,
        flatX: Float = 0f, flatY: Float = 0f, fourWay: Boolean = false
    ): Int {
        val configured = if (deadzone.isFinite()) deadzone.coerceIn(0f, 1f) else 0.5f
        fun centered(value: Float, flat: Float): Float {
            if (!value.isFinite()) return 0f
            val threshold = maxOf(configured, if (flat.isFinite()) flat.coerceIn(0f, 1f) else 0f)
            val bounded = value.coerceIn(-1f, 1f)
            return if (direction(bounded, threshold) == 0) 0f else bounded
        }
        val x = centered(rawX, flatX)
        val y = centered(rawY, flatY)
        val ax = kotlin.math.abs(x)
        val ay = kotlin.math.abs(y)
        if (ax == 0f && ay == 0f) return 0
        val horizontal = if (x < 0f) LEFT else RIGHT
        val vertical = if (y < 0f) UP else DOWN
        if (fourWay) return if (ax >= ay) horizontal else vertical
        var mask = 0
        if (ax > 0f && ax >= ay * DIAGONAL_RATIO) mask = mask or horizontal
        if (ay > 0f && ay >= ax * DIAGONAL_RATIO) mask = mask or vertical
        return mask
    }

    fun controllerButtons(
        x: Float, y: Float, hatX: Float, hatY: Float, deadzone: Float,
        flatX: Float = 0f, flatY: Float = 0f, fourWay: Boolean = false
    ): Int {
        val stick = buttons(x, y, deadzone, flatX, flatY, fourWay)
        val hat = buttons(hatX, hatY, 0.5f, fourWay = fourWay)
        // Noise on either source must not steal the other control. A deliberate
        // stick push wins; a real HAT press wins over small stick-center drift.
        val strongStick = (x.isFinite() && kotlin.math.abs(x) > 0.5f) ||
            (y.isFinite() && kotlin.math.abs(y) > 0.5f)
        return when { stick == 0 -> hat; hat == 0 || strongStick -> stick; else -> hat }
    }
}

/** Exactly one pen pointer; lifting it releases the pen instead of switching fingers. */
class DsPenPointer(private val send: (Boolean, Int) -> Unit) {
    var activeId = -1
        private set

    fun down(id: Int, x: Float, y: Float, rect: FloatArray): Boolean {
        if (activeId != -1) return false
        activeId = id
        return move(id, x, y, rect)
    }

    fun move(id: Int, x: Float, y: Float, rect: FloatArray): Boolean {
        if (activeId == -1 || activeId != id) return false
        val point = DsTouchCoordinates.map(x, y, rect)
        send(point != null, point ?: 0)
        return point != null
    }

    fun up(id: Int) { if (id == activeId) cancel() }

    fun cancel(emitRelease: Boolean = true) { activeId = -1; if (emitRelease) send(false, 0) }
}
