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
    fun staleAwayWithFieldGoneIsCleared() {
        val state = TypingStateGuard.State(away = true)
        assertTrue(state.stale)
        assertEquals(TypingStateGuard.State(), TypingStateGuard.reconcile(state, showing = false, picking = false))
    }

    @Test
    fun fieldGoneWithNoPickerClearsGeometryAndVeilTogether() {
        val state = TypingStateGuard.State(typingTop = 480, away = true)
        val result = TypingStateGuard.reconcile(state, showing = false, picking = false)
        assertEquals(0, result.typingTop)
        assertFalse(result.away)
        assertEquals(TypingStateGuard.State(), result)
    }

    @Test
    fun genuinePickerKeepsTheVeilEvenWhileFieldIsPaused() {
        val state = TypingStateGuard.State(typingTop = 480, away = true)
        assertEquals(state, TypingStateGuard.reconcile(state, showing = false, picking = true))
    }

    @Test
    fun fieldUpWithStaleVeilClearsGeometryAndAwayTogether() {
        val state = TypingStateGuard.State(typingTop = 480, away = true)
        assertEquals(TypingStateGuard.State(), TypingStateGuard.reconcile(state, showing = true, picking = false))
    }

    @Test
    fun pickerDeadlineUsesThePureHelper() {
        val started = 10_000L
        val deadline = TypingStateGuard.PICKER_DEADLINE_MS
        assertFalse(TypingStateGuard.pickerExpired(0L, started + deadline * 2))
        assertFalse(TypingStateGuard.pickerExpired(started, started + deadline - 1))
        assertTrue(TypingStateGuard.pickerExpired(started, started + deadline))
        assertTrue(TypingStateGuard.pickerExpired(started, started + deadline + 1))
        assertTrue(TypingStateGuard.pickerExpired(started, started + 1_000L, deadlineMs = 1_000L))
    }

    @Test
    fun aCleanFieldInThePickerIsUntouched() {
        val state = TypingStateGuard.State()
        assertFalse(state.stale)
        assertEquals(state, TypingStateGuard.reconcile(state, showing = true, picking = true))
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
        assertEquals(state, TypingStateGuard.reconcile(state, showing = true, picking = false))
        assertEquals(state, TypingStateGuard.reconcile(state, showing = false, picking = true))
    }
}
