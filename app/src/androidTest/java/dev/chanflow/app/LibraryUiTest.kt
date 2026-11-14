package dev.chanflow.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

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
        runBlocking { app.vault.file(post).writeBytes(bytes); app.vault.record(post) }
        try {
            compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
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
            instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/chanflow-video.png").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        } finally { runBlocking { app.vault.removeHistory(setOf(post.key)); app.vault.delete(post) } }
    }

    private fun findPlayer(view: android.view.View): androidx.media3.ui.PlayerView? {
        if (view is androidx.media3.ui.PlayerView) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
