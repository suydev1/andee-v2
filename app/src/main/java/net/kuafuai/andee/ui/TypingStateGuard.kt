package net.kuafuai.andee.ui

/**
 * The overlay's typing-session state, kept separate from the window so the
 * "field is gone but the card never got the news" case is testable.
 *
 * [FloatingWindowUi.setAway] and [FloatingWindowUi.setTypingTop] are callbacks
 * from [TextInputActivity]. A field that is killed, or whose `picking` flag is
 * stuck true after the camera/picker went away, leaves the card invisible and
 * untouchable — `away = true` plus a non-zero top edge is exactly
 * `alpha 0 / FLAG_NOT_TOUCHABLE`. The card has no way to ask the user to tap it
 * when that happens, so the state has to be reconciled against the field's
 * authoritative "on screen / in a picker" answer. A field on screen but not in
 * a picker must not keep the veil from a picker that has already gone.
 */
object TypingStateGuard {

    data class State(val typingTop: Int = 0, val away: Boolean = false) {
        val stale: Boolean get() = typingTop > 0 || away
    }

    /** Forget the borrowed geometry once no field owns it; lift the picker veil when no picker is up. */
    fun reconcile(state: State, showing: Boolean, picking: Boolean): State = when {
        !showing -> State()
        !picking && state.away -> state.copy(away = false)
        else -> state
    }
}
