package dev.chanflow.app

import org.junit.Assert.*
import org.junit.Test

class MediaRulesTest {
    @Test fun validAttachmentsAndThumbnails() {
        listOf("https://i.4cdn.org/w/123456789.jpg", "https://i.4cdn.org/g/123456789s.jpg",
            "https://i.4cdn.org/gif/123456789.webm").forEach { assertTrue(it, MediaRules.validMediaUrl(it)) }
    }
    @Test fun rejectHostOverrideAndNonMediaPaths() {
        listOf("http://i.4cdn.org/w/123.jpg", "https://i.4cdn.org.evil.test/w/123.jpg",
            "https://evil.test@i.4cdn.org/w/123.jpg", "https://i.4cdn.org:443/w/123.jpg",
            "https://i.4cdn.org/w/../123.jpg", "https://i.4cdn.org/w/123.jpg?url=x",
            "https://i.4cdn.org/w/123.apk", "file:///tmp/123.jpg").forEach { assertFalse(it, MediaRules.validMediaUrl(it)) }
    }
    @Test fun metadataRoundTripPreservesThreadIdentityAndUnicode() {
        val p = MediaPost("w", 12, 13, 123456, ".jpg", "日本語 & picture", "A thread", "quote\n> reply", 1920, 1080, 1000)
        assertEquals(p, MediaPost.fromJson(p.json()))
        assertEquals("w_13", p.key)
        assertFalse(MediaRules.valid(p.copy(extension = "/../../elsewhere")))
        assertFalse(MediaRules.valid(p.copy(bytes = MediaRules.MAX_BYTES + 1)))
    }
    @Test fun releaseComparisonUsesNumbersRatherThanLexicalOrder() {
        assertTrue(AppUpdates.compareVersions("0.10.0", "0.9.9") > 0)
        assertEquals(0, AppUpdates.compareVersions("1.2.3", "1.2.3"))
        assertTrue(AppUpdates.compareVersions("1.2.3", "2.0.0") < 0)
    }
}
