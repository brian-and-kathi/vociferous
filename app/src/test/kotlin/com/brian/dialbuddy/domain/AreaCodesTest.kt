package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaCodesTest {
    @Test
    fun nationalAndCountryCodeNumbersYieldTheAreaCode() {
        assertEquals("212", areaCodeOf("2125550100"))
        assertEquals("212", areaCodeOf("12125550100"))
        assertEquals("212", areaCodeOf("+1 (212) 555-0100"))
        assertEquals("212", areaCodeOf("212-555-0100,99"))
    }

    @Test
    fun shortInternationalAndInvalidPrefixesHaveNoAreaCode() {
        assertNull(areaCodeOf(""))
        assertNull(areaCodeOf("5550100"))
        assertNull(areaCodeOf("+44 20 7946 0958"))
        assertNull(areaCodeOf("1125550100"))
    }

    @Test
    fun savedCodesAreThreeDigitsStartingFromTwo() {
        assertEquals("305", normalizeAreaCode(" 305 "))
        assertNull(normalizeAreaCode("30"))
        assertNull(normalizeAreaCode("130"))
        assertNull(normalizeAreaCode("30a"))
        assertEquals(listOf("212", "646"), parseAreaCodes("646, 212, 646, nope"))
        assertEquals("212,646", formatAreaCodes(listOf("646", "212", "12")))
    }
}
