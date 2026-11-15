package dev.chanflow.app

import org.junit.Assert.*
import org.junit.Test

class SavedThreadsTest {
    private fun file(board: String, thread: Long, post: Long, saved: Long) =
        MediaPost(board, thread, post, 100000 + post, ".jpg", "File $post", "Thread $thread", "", bytes = 100, savedAt = saved)

    @Test fun groupsByBoardAndThreadAndKeepsOriginalPostOrder() {
        val groups = groupSavedThreads(listOf(file("w", 42, 3, 30), file("g", 42, 2, 20), file("w", 42, 1, 10)))
        assertEquals(2, groups.size)
        assertEquals("w_42", groups.first().key)
        assertEquals(listOf(1L, 3L), groups.first().files.map { it.post })
        assertEquals(200L, groups.first().bytes)
    }
    @Test fun emptyTitlesStillGiveFoldersReadableNamesAndEmptyLibrariesWork() {
        assertEquals("Thread #42", groupSavedThreads(listOf(file("w", 42, 1, 10).copy(title = ""))).single().title)
        assertTrue(groupSavedThreads(emptyList()).isEmpty())
    }
}
