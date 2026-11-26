package net.kuafuai.andee.ui

/**
 * The overlay's typing-session state, kept separate from the window so the
 * "field is gone but the card never got the news" case is testable.
 *
 * [FloatingWindowUi.setAway] and [FloatingWindowUi.setTypingTop] are callbacks
 * from [TextInputActivity]. A field that is killed, backgrounded through a
 * picker that never returns a result, or torn down while a new service owns
 * the callbacks can leave the card invisible and untouchable — `away = true`
 * plus a non-zero top edge is exactly `alpha 0 / FLAG_NOT_TOUCHABLE`. The card
 * has no way to ask the user to tap it when that happens, so the state has to
 * be reconciled against the authoritative "is the field actually up?" answer.
 */
object TypingStateGuard {

    /** How long a picker may own the veil after the field hands the screen over. */
    const val PICKER_DEADLINE_MS = 30_000L

    /**
     * Whether a picker that started at [pickingAt] has run out its deadline. Pure, so the deadline itself is
     * unit-testable without a device clock; a [pickingAt] of 0 means no picker ever started.
     */
    fun pickerExpired(pickingAt: Long, now: Long, deadlineMs: Long = PICKER_DEADLINE_MS): Boolean =
        pickingAt > 0L && now - pickingAt >= deadlineMs

    data class State(val typingTop: Int = 0, val away: Boolean = false) {
        val stale: Boolean get() = typingTop > 0 || away
    }

    /**
     * Forget the borrowed geometry and the picker veil once no field owns them.
     *
     * `typingTop` and `away` are one unit, never two independent flags. A non-zero `typingTop` resizes the
     * overlay and leaves it `FLAG_NOT_TOUCHABLE`; `away = true` makes it alpha 0. Clearing either alone leaves
     * an invisible, untouchable card with no way for the user to tap it — so every reset below is `State()`.
     *
     * The picker is checked first, and it wins even when the field reports itself off-screen: a genuine picker
     * pauses the field (`showing = false, picking = true`) and the card must stay out of that app's way. Once
     * the field says the picker is gone, any leftover geometry or veil is stale and both go together.
     */
    fun reconcile(state: State, showing: Boolean, picking: Boolean): State = when {
        picking -> state
        !showing -> State()
        state.stale -> State()
        else -> state
    }
}
