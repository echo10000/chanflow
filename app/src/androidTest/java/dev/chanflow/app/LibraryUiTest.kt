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
            val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(app.cacheDir, "qa-offline.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { runBlocking { app.vault.removeHistory(setOf(post.key)); app.vault.delete(post) } }
    }
}
