package net.kuafuai.andee.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repeating-todo floor lives in core so both bodies enforce the same minimum. */
class CronExprTest {

    @Test fun minimumIntervalIsFiveMinutes() {
        assertEquals(5, CronExpr.MIN_INTERVAL_MINUTES)
    }

    @Test fun aFiveMinuteExpressionMeetsTheMinimum() {
        val gap = CronExpr.parse("*/5 * * * *").minGapMinutes()
        assertEquals(5, gap)
        assertTrue(gap >= CronExpr.MIN_INTERVAL_MINUTES)
    }
}
