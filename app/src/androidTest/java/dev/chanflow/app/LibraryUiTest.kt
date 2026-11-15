package dev.chanflow.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun opensASavedImageFromTheLibraryWithoutNetworkMedia() {
        val app = compose.activity.application as FlowApplication
        val post = MediaPost("w", 10001, 10002, 123456789, ".png", "offline-ui-fixture", "Offline fixture", "A local image", 64, 64, 120)
        runBlocking {
            app.vault.file(post).outputStream().use { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(120, 145, 115)) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
            app.vault.record(post)
        }
        try {
            compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Open saved thread w_10001").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Open saved thread w_10001").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("offline-ui-fixture.png").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("offline-ui-fixture.png").performClick()
            compose.onNodeWithContentDescription("offline-ui-fixture").assertIsDisplayed()
            compose.onNodeWithText("/w/  #10001  / Offline").assertIsDisplayed()
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("screencap -p /data/local/tmp/chanflow-qa.png").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        } finally { runBlocking { app.vault.removeHistory(setOf(post.key)); app.vault.delete(post) } }
    }

    @Test fun playsASavedWebmInsideTheApp() {
        val app = compose.activity.application as FlowApplication
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("playback.webm").use { it.readBytes() }
        val post = MediaPost("w", 10201, 10202, 123456780, ".webm", "offline-video-fixture", "Offline video", "A locally saved video", 320, 180, bytes.size.toLong())
        val next = post.copy(post = 10203, timestamp = 123456781, filename = "offline-video-next")
        val another = post.copy(thread = 10301, post = 10302, timestamp = 123456782, filename = "offline-thread-next", title = "Another saved thread")
        runBlocking {
            app.vault.file(another).writeBytes(bytes); app.vault.record(another); kotlinx.coroutines.delay(10)
            app.vault.file(next).writeBytes(bytes); app.vault.record(next); kotlinx.coroutines.delay(10)
            app.vault.file(post).writeBytes(bytes); app.vault.record(post)
        }
        try {
            compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Open saved thread w_10201").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Open saved thread w_10201").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("offline-video-fixture.webm").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("offline-video-fixture.webm").performClick()
            compose.waitUntil(12000) {
                var playing = false
                instrumentation.runOnMainSync {
                    val player = findPlayer(compose.activity.window.decorView)?.player
                    playing = player != null && player.playerError == null && player.isPlaying && player.currentPosition > 150
                }
                playing
            }
            var firstPlayer = 0
            instrumentation.runOnMainSync { firstPlayer = System.identityHashCode(findPlayer(compose.activity.window.decorView)?.player) }
            compose.onNodeWithContentDescription("Video position", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Media feed", useUnmergedTree = true).performTouchInput { click(center) }
            compose.waitUntil(3000) {
                var paused = false
                instrumentation.runOnMainSync { paused = findPlayer(compose.activity.window.decorView)?.player?.playWhenReady == false }
                paused
            }
            compose.onNodeWithContentDescription("Media feed", useUnmergedTree = true).performTouchInput { click(center) }
            compose.onNodeWithContentDescription("Media feed", useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitUntil(10000) { compose.onAllNodesWithText("offline-video-next.webm").fetchSemanticsNodes().isNotEmpty() }
            instrumentation.runOnMainSync {
                org.junit.Assert.assertEquals("Swiping reuses the same native player", firstPlayer,
                    System.identityHashCode(findPlayer(compose.activity.window.decorView)?.player))
            }
            compose.onNodeWithContentDescription("Media feed", useUnmergedTree = true).performTouchInput { swipeLeft() }
            compose.waitUntil(10000) { compose.onAllNodesWithText("offline-thread-next.webm").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 / 1").assertIsDisplayed()
            compose.onNodeWithContentDescription("Media feed", useUnmergedTree = true).performTouchInput { swipeRight() }
            compose.waitUntil(10000) { compose.onAllNodesWithText("offline-video-next.webm").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("2 / 2").assertIsDisplayed()
            instrumentation.runOnMainSync {
                org.junit.Assert.assertEquals("Thread changes also reuse the player", firstPlayer,
                    System.identityHashCode(findPlayer(compose.activity.window.decorView)?.player))
            }
            instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/chanflow-video.png").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        } finally { runBlocking { app.vault.removeHistory(setOf(post.key, next.key, another.key)); app.vault.delete(post); app.vault.delete(next); app.vault.delete(another) } }
    }

    @Test fun backRestoresTheThreadListAndFileGridScrollPositions() {
        val app = compose.activity.application as FlowApplication
        val image = java.io.ByteArrayOutputStream().apply { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.DKGRAY) }.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        val folders = (0..15).map { i -> MediaPost("w", 30000L + i, 31000L + i, 123400000L + i, ".png", "folder-file-$i", "Saved thread $i", "", 64, 64, image.size.toLong()) }
        val files = (0..29).map { i -> MediaPost("w", 40000, 41000L + i, 123500000L + i, ".png", "scroll-file-$i", "Many saved files", "", 64, 64, image.size.toLong()) }
        val all = folders + files
        runBlocking { all.forEach { app.vault.file(it).writeBytes(image); app.vault.record(it); kotlinx.coroutines.delay(2) } }
        try {
            compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Saved threads").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Saved threads").performScrollToIndex(10)
            compose.onNodeWithContentDescription("Open saved thread w_30006").assertIsDisplayed().performClick()
            compose.onNodeWithText("folder-file-6.png").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("All threads", substring = true).performClick()
            compose.onNodeWithContentDescription("Open saved thread w_30006").assertIsDisplayed()
            compose.onNodeWithContentDescription("Saved threads").performScrollToIndex(0)
            compose.onNodeWithContentDescription("Open saved thread w_40000").performClick()
            compose.onNodeWithContentDescription("Saved files").performScrollToIndex(22)
            compose.onNodeWithText("scroll-file-22.png").assertIsDisplayed().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("scroll-file-22.png").assertIsDisplayed()
        } finally { runBlocking { app.vault.removeHistory(all.map { it.key }.toSet()); all.forEach { app.vault.delete(it) } } }
    }

    private fun findPlayer(view: android.view.View): androidx.media3.ui.PlayerView? {
        if (view is androidx.media3.ui.PlayerView) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
