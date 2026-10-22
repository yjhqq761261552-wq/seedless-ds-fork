package com.seedlessds.app.emu

/** Persist conflicts as -1; removing a preference restores a DS button's default. */
internal object KeyBindings {
    data class Resolved(val ds: Map<Int, Int>, val functions: Map<Int, Int>)
    fun resolve(buttons: Map<Int, Int>, functions: Map<Int, Int>): Resolved {
        val ds = linkedMapOf<Int, Int>()
        val special = linkedMapOf<Int, Int>()
        buttons.forEach { (index, key) -> if (key >= 0 && key !in ds) ds[key] = index }
        functions.forEach { (fn, key) -> if (key >= 0 && key !in ds && key !in special) special[key] = fn }
        return Resolved(ds, special)
    }

    fun displacedButtons(buttons: Map<Int, Int>, key: Int, except: Int = -1): List<Int> =
        buttons.filter { (index, assigned) -> index != except && assigned == key }.keys.toList()
}
