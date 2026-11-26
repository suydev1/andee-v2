package net.kuafuai.andee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingStateGuardTest {

    @Test
    fun staleTopWithFieldGoneIsCleared() {
        val state = TypingStateGuard.State(typingTop = 480)
        assertTrue(state.stale)
        assertEquals(TypingStateGuard.State(), TypingStateGuard.reconcile(state, showing = false, picking = false))
    }

    @Test
    fun staleAwayWithFieldGoneIsClearedEvenIfPickingIsStuck() {
        val state = TypingStateGuard.State(away = true)
        assertTrue(state.stale)
        assertEquals(TypingStateGuard.State(), TypingStateGuard.reconcile(state, showing = false, picking = true))
    }

    @Test
    fun aLiveFieldWithNoPickerLiftsTheVeilButKeepsItsGeometry() {
        val state = TypingStateGuard.State(typingTop = 480, away = true)
        assertEquals(
            TypingStateGuard.State(typingTop = 480),
            TypingStateGuard.reconcile(state, showing = true, picking = false),
        )
    }

    @Test
    fun aFieldInThePickerKeepsItsAwayState() {
        val state = TypingStateGuard.State(typingTop = 480, away = true)
        assertEquals(state, TypingStateGuard.reconcile(state, showing = true, picking = true))
    }

    @Test
    fun aCleanStateIsUntouched() {
        val state = TypingStateGuard.State()
        assertFalse(state.stale)
        assertEquals(state, TypingStateGuard.reconcile(state, showing = false, picking = false))
    }
}
