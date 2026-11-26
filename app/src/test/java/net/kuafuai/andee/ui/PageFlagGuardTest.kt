package net.kuafuai.andee.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFlagGuardTest {

    @Test
    fun aLivePageOwnsTheScreen() {
        assertTrue(PageFlagGuard.ownsScreen(flag = true, activityShowing = true))
    }

    @Test
    fun aDeadPageCannotKeepTheAssistantHidden() {
        assertFalse(PageFlagGuard.ownsScreen(flag = true, activityShowing = false))
    }

    @Test
    fun aClearedFlagNeverOwnsTheScreen() {
        assertFalse(PageFlagGuard.ownsScreen(flag = false, activityShowing = true))
    }
}
