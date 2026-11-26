package net.kuafuai.andee.ui

/** A persisted "page is open" flag is only true while its Activity is alive. */
object PageFlagGuard {

    fun ownsScreen(flag: Boolean, activityShowing: Boolean): Boolean = flag && activityShowing
}
