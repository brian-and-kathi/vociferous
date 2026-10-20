package com.brian.dialbuddy.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    @Test
    fun theSameReleaseIsNotNewer() {
        assertFalse(isNewerRelease("1.4.0", "1.4.0"))
        assertFalse(isNewerRelease("1.4.0+", "1.4.0"))
        assertFalse(isNewerRelease("v1.4.0", "1.4.0"))
    }

    @Test
    fun aHigherNumberIsNewer() {
        assertTrue(isNewerRelease("1.3.0", "1.4.0"))
        assertTrue(isNewerRelease("1.4.0", "1.4.1"))
        assertTrue(isNewerRelease("1.4.0", "1.10.0"))
        assertTrue(isNewerRelease("1.4", "1.4.1"))
    }

    @Test
    fun aLowerOrUnreadableNameIsNotNewer() {
        assertFalse(isNewerRelease("1.5.0", "1.4.0"))
        assertFalse(isNewerRelease("1.4.0", "latest"))
        assertFalse(isNewerRelease("", "1.4.0"))
    }
}
