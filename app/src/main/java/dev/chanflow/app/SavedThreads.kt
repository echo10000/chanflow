package dev.chanflow.app

data class SavedThread(val board: String, val thread: Long, val title: String, val files: List<MediaPost>) {
    val key get() = "${board}_$thread"
    val bytes get() = files.sumOf { it.bytes }
    val latestSave get() = files.maxOfOrNull { it.savedAt } ?: 0
}

fun groupSavedThreads(posts: List<MediaPost>): List<SavedThread> = posts
    .groupBy { it.board to it.thread }
    .map { (identity, files) -> SavedThread(identity.first, identity.second,
        files.firstOrNull { it.title.isNotBlank() }?.title ?: "Thread #${identity.second}", files.sortedBy { it.post }) }
    .sortedByDescending { it.latestSave }
