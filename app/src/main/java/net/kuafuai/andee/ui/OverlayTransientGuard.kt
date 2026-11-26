package net.kuafuai.andee.ui

/**
 * A transient overlay flag that must disappear on its own if its owner dies
 * without releasing it. [until] is an uptime deadline; 0 means not active.
 */
object OverlayTransientGuard {

    fun expired(active: Boolean, until: Long, now: Long): Boolean =
        active && until > 0L && now >= until
}
