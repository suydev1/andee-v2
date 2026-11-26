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

    data class State(val typingTop: Int = 0, val away: Boolean = false) {
        val stale: Boolean get() = typingTop > 0 || away
    }

    /** Forget the borrowed geometry and the picker veil once no field owns them. */
    fun reconcile(state: State, showing: Boolean, picking: Boolean): State =
        if (!showing && !picking && state.stale) State() else state
}
