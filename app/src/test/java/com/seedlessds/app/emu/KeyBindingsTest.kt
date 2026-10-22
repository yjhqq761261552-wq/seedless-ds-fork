package com.seedlessds.app.emu

import org.junit.Assert.*
import org.junit.Test

class KeyBindingsTest {
    @Test fun rewindCanUseAnUnassignedPhysicalKey() {
        val result = KeyBindings.resolve(mapOf(4 to 96), mapOf(10 to 109))
        assertEquals(4, result.ds[96])
        assertEquals(10, result.functions[109])
    }
    @Test fun bindingRewindDisablesDefaultButtonRatherThanRestoringIt() {
        val assigned = mapOf(4 to 96, 5 to 97)
        val disabled = assigned.toMutableMap()
        KeyBindings.displacedButtons(assigned, 96).forEach { disabled[it] = -1 }
        val result = KeyBindings.resolve(disabled, mapOf(10 to 96))
        assertFalse(result.ds.containsKey(96))
        assertEquals(10, result.functions[96])
        assertEquals(5, result.ds[97])
    }
    @Test fun rebindingButtonPreservesItsOwnAssignmentAndDisplacesOtherButtons() {
        assertEquals(listOf(5), KeyBindings.displacedButtons(mapOf(4 to 96, 5 to 96), 96, 4))
    }
    @Test fun legacyConflictingPreferencesStillHaveDeterministicPriority() {
        val result = KeyBindings.resolve(linkedMapOf(4 to 96, 5 to 96), linkedMapOf(1 to 96, 10 to 109, 2 to 109))
        assertEquals(mapOf(96 to 4), result.ds)
        assertEquals(mapOf(109 to 10), result.functions)
    }
    @Test fun clearingRewindLeavesKeyAvailableAndIgnoresNegativeAssignments() {
        val result = KeyBindings.resolve(mapOf(4 to -1), mapOf(10 to -1))
        assertTrue(result.ds.isEmpty())
        assertTrue(result.functions.isEmpty())
    }
}
