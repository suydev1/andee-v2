package net.kuafuai.andee.device

import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoiceTest {
    @Test fun `a known viewer wins but a resolver still works`() {
        assertEquals("com.adobe.reader", AppChoice.choose(listOf("com.other", "com.adobe.reader"), "application/pdf"))
        assertEquals("com.other", AppChoice.choose(listOf("com.other"), "application/pdf"))
        assertEquals(null, AppChoice.choose(emptyList(), "application/pdf"))
    }
}
