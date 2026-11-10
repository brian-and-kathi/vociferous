package com.brian.dialbuddy.ui

import com.brian.dialbuddy.ui.components.pagerAcceptsUserScroll
import com.brian.dialbuddy.ui.components.pagerShouldSettle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPagerTest {
    @Test
    fun aLonePageDoesNotScrollAndADraggedEndSpringsBack() {
        assertFalse(pagerAcceptsUserScroll(1))
        assertFalse(pagerAcceptsUserScroll(0))
        assertTrue(pagerAcceptsUserScroll(2))
        assertFalse(pagerShouldSettle(scrolling = true, offsetFraction = 0.2f))
        assertFalse(pagerShouldSettle(scrolling = false, offsetFraction = 0f))
        assertTrue(pagerShouldSettle(scrolling = false, offsetFraction = 0.04f))
        assertTrue(pagerShouldSettle(scrolling = false, offsetFraction = -0.04f))
    }
}
