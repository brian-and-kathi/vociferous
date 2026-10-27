package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogTest {
    @Test
    fun updateKeepsTheOriginalPostedTimeAndClearsDismissed() {
        val posted = recordPosted(emptyList(), notice("a", postedAt = 10, updatedAt = 10, title = "Old"))
        val dismissed = recordDismissed(posted, "a", 20)
        val again = recordPosted(
            dismissed,
            notice("a", postedAt = 99, updatedAt = 30, title = "New", text = "Next"),
        )
        val row = again.single()
        assertEquals(10, row.postedAt)
        assertEquals(30, row.updatedAt)
        assertEquals("New", row.title)
        assertEquals("Next", row.text)
        assertFalse(row.dismissed)
    }

    @Test
    fun dismissalKeepsTheText() {
        val posted = recordPosted(emptyList(), notice("a", updatedAt = 10, text = "Hello"))
        val dismissed = recordDismissed(posted, "a", 11)
        assertEquals("Hello", dismissed.single().text)
        assertTrue(dismissed.single().dismissed)
        assertEquals(11, dismissed.single().updatedAt)
    }

    @Test
    fun capKeepsTheNewest400() {
        val posted = (1..NOTICE_LIMIT + 1).fold(emptyList<Notice>()) { list, index ->
            recordPosted(list, notice("k$index", updatedAt = index.toLong()))
        }
        assertEquals(NOTICE_LIMIT, posted.size)
        assertEquals("k${NOTICE_LIMIT + 1}", posted.first().key)
        assertFalse(posted.any { it.key == "k1" })
    }

    @Test
    fun ownNotificationsAreSkipped() {
        val kept = recordPosted(
            listOf(notice("a", updatedAt = 1)),
            notice("b", updatedAt = 2, packageName = OWN_PACKAGE),
        )
        assertEquals(listOf("a"), kept.map { it.key })
        assertTrue(recordPosted(emptyList(), notice("", updatedAt = 1)).isEmpty())
    }

    @Test
    fun syncDismissesRowsThatAreNoLongerPosted() {
        val current = listOf(
            notice("live", updatedAt = 10, title = "Old"),
            notice("gone", updatedAt = 9, text = "Keep"),
            notice("already", updatedAt = 8, dismissed = true, text = "Done"),
        )
        val synced = syncNotices(
            current,
            listOf(notice("live", postedAt = 50, updatedAt = 12, title = "New"), notice("fresh", updatedAt = 13)),
            now = 12,
        )
        val gone = synced.single { it.key == "gone" }
        assertTrue(gone.dismissed)
        assertEquals("Keep", gone.text)
        val live = synced.single { it.key == "live" }
        assertFalse(live.dismissed)
        assertEquals(10, live.postedAt)
        assertEquals("New", live.title)
        assertTrue(synced.single { it.key == "already" }.dismissed)
        assertTrue(synced.any { it.key == "fresh" })
    }

    @Test
    fun appsFollowTheNewestNotice() {
        val notices = listOf(
            notice("a", updatedAt = 1, packageName = "com.old", appName = "Old"),
            notice("b", updatedAt = 5, packageName = "com.new", appName = "New"),
            notice("c", updatedAt = 3, packageName = "com.old", appName = "Older"),
        )
        val apps = noticeApps(notices)
        assertEquals(listOf("com.new", "com.old"), apps.map { it.packageName })
        assertEquals("Older", apps.single { it.packageName == "com.old" }.appName)
        assertEquals(2, apps.single { it.packageName == "com.old" }.count)
        assertEquals(1, apps.single { it.packageName == "com.new" }.count)
    }

    @Test
    fun removeOneNoticeOrAWholeApp() {
        val notices = listOf(
            notice("a", updatedAt = 1, packageName = "com.a"),
            notice("b", updatedAt = 2, packageName = "com.a"),
            notice("c", updatedAt = 3, packageName = "com.b"),
        )
        assertEquals(listOf("b", "c"), removeNotices(notices, setOf("a")).map { it.key })
        val appKeys = notices.filter { it.packageName == "com.a" }.map { it.key }.toSet()
        assertEquals(listOf("c"), removeNotices(notices, appKeys).map { it.key })
        assertEquals(notices, removeNotices(notices, emptySet()))
    }

    @Test
    fun fileFormatRoundTripsEscapedTextAndSkipsACorruptLine() {
        val original = listOf(
            notice("a", updatedAt = 3, title = "a\\b", text = "line\nnext${'\u001f'}sep\r"),
            notice("b", updatedAt = 2, dismissed = true),
        )
        assertEquals(original, decodeNotices(encodeNotices(original)))
        assertEquals(original, decodeNotices("not a row\n" + encodeNotices(original)))
        val withIcon = notice("c", updatedAt = 4, iconName = "face.png")
        assertEquals("face.png", decodeNotices(encodeNotices(listOf(withIcon))).single().iconName)
        val legacy = listOf("k", "pkg", "App", "Title", "Body", "1", "2", "0").joinToString("\u001f")
        assertEquals(null, decodeNotices(legacy).single().iconName)
    }

    @Test
    fun ignoredSendersAreNotLoggedAndCanBeAllowedAgain() {
        val ignored = withIgnored(emptyList(), "com.petlibro", "Petlibro")
        assertEquals(listOf("com.petlibro"), ignored.map { it.packageName })
        assertEquals("Petlibro", ignored.single().appName)
        val renamed = withIgnored(ignored, "com.petlibro", "Pet Libro")
        assertEquals(listOf("Pet Libro"), renamed.map { it.appName })
        assertEquals(emptyList<IgnoredSender>(), withoutIgnored(renamed, "com.petlibro"))
        assertEquals(renamed, decodeIgnored(encodeIgnored(renamed)))

        val packages = renamed.map { it.packageName }.toSet()
        val current = listOf(notice("old", updatedAt = 1, packageName = "com.petlibro", appName = "Petlibro"))
        assertEquals(current, recordPosted(current, notice("new", updatedAt = 2, packageName = "com.petlibro"), ignored = packages))
        val synced = syncNotices(
            current,
            listOf(notice("live", updatedAt = 3, packageName = "com.petlibro"), notice("other", updatedAt = 4, packageName = "com.other")),
            now = 5,
            ignored = packages,
        )
        assertEquals(listOf("other"), synced.map { it.key })
        assertEquals(
            listOf("other"),
            syncNotices(synced, emptyList(), now = 6, ignored = emptySet()).map { it.key },
        )
    }

    @Test
    fun updateKeepsThePreviousAvatarWhenTheNewOneIsMissing() {
        val first = recordPosted(emptyList(), notice("a", updatedAt = 1, iconName = "a.png"))
        val kept = recordPosted(first, notice("a", updatedAt = 2))
        val replaced = recordPosted(kept, notice("a", updatedAt = 3, iconName = "b.png"))
        assertEquals("a.png", kept.single().iconName)
        assertEquals("b.png", replaced.single().iconName)
    }

    private fun notice(
        key: String,
        updatedAt: Long,
        postedAt: Long = updatedAt,
        title: String = "Title",
        text: String = "Body",
        packageName: String = "com.example.app",
        appName: String = "Example",
        dismissed: Boolean = false,
        iconName: String? = null,
    ) = Notice(
        key = key,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        postedAt = postedAt,
        updatedAt = updatedAt,
        dismissed = dismissed,
        iconName = iconName,
    )
}
