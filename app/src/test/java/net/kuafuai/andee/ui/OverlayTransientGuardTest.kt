package net.kuafuai.andee.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTransientGuardTest {

    @Test
    fun anExpiredCaptureIsReclaimed() {
        assertTrue(OverlayTransientGuard.expired(active = true, until = 1_000L, now = 1_001L))
    }

    @Test
    fun aLiveCaptureIsKept() {
        assertFalse(OverlayTransientGuard.expired(active = true, until = 1_000L, now = 999L))
    }

    @Test
    fun anInactiveFlagIsNeverExpired() {
        assertFalse(OverlayTransientGuard.expired(active = false, until = 1_000L, now = 9_999L))
    }

    @Test
    fun aFlagWithNoDeadlineIsNeverExpired() {
        assertFalse(OverlayTransientGuard.expired(active = true, until = 0L, now = 9_999L))
    }
}
