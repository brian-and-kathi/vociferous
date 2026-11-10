package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun aReleaseCheckIsDueADayAfterTheLastOne() {
        assertFalse(isReleaseCheckDue(1_000L, 1_000L + MILLIS_PER_DAY - 1))
        assertTrue(isReleaseCheckDue(1_000L, 1_000L + MILLIS_PER_DAY))
        assertTrue(isReleaseCheckDue(0L, MILLIS_PER_DAY))
    }

    @Test
    fun aPromptShowsOnceUntilThatVersionIsDismissed() {
        assertTrue(shouldPromptForUpdate("1.10.0", dismissedVersion = null, promptedVersion = null))
        assertFalse(shouldPromptForUpdate("1.10.0", dismissedVersion = "1.10.0", promptedVersion = null))
        assertFalse(shouldPromptForUpdate("1.10.0", dismissedVersion = null, promptedVersion = "1.10.0"))
        assertTrue(shouldPromptForUpdate("1.11.0", dismissedVersion = "1.10.0", promptedVersion = "1.10.0"))
        assertFalse(shouldPromptForUpdate(" ", dismissedVersion = null, promptedVersion = null))
    }

    @Test
    fun anOfferRequiresANewerHttpsRelease() {
        val offer = releaseOffer("v1.10.0", "https://example.com/app.apk", "1.9.1+")
        assertEquals("1.10.0", offer?.versionName)
        assertEquals("https://example.com/app.apk", offer?.apkUrl)
        assertNull(releaseOffer("1.10.0", "https://example.com/app.apk", "1.10.0+"))
        assertNull(releaseOffer("1.11.0", "http://example.com/app.apk", "1.10.0"))
        assertNull(releaseOffer("1.11.0", null, "1.10.0"))
    }

    @Test
    fun onlyANewerVociferousPackageCanInstall() {
        assertTrue(isInstallableUpgrade("com.brian.dialbuddy", 14L, 13L))
        assertFalse(isInstallableUpgrade("com.brian.dialbuddy", 13L, 13L))
        assertFalse(isInstallableUpgrade("com.brian.dialbuddy", 12L, 13L))
        assertFalse(isInstallableUpgrade("dev.emptyvoicemail", 99L, 13L))
        assertFalse(isInstallableUpgrade(null, 99L, 13L))
    }

    @Test
    fun anUpdateArchiveStartsWithZipMagic() {
        assertTrue(hasZipMagic(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertFalse(hasZipMagic(byteArrayOf('P'.code.toByte())))
        assertFalse(hasZipMagic(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte())))
    }
}
