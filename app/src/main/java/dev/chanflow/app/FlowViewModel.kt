package dev.chanflow.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FlowViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as FlowApplication
    private val preferences = application.getSharedPreferences("settings", 0)
    var boards by mutableStateOf<List<Board>>(emptyList()); private set
    var threads by mutableStateOf<List<ThreadCard>>(emptyList()); private set
    var saved by mutableStateOf<List<MediaPost>>(emptyList()); private set
    var history by mutableStateOf<List<Watch>>(emptyList()); private set
    var board by mutableStateOf(preferences.getString("board", "wsg") ?: "wsg"); private set
    var tab by mutableStateOf("browse"); private set
    var feed by mutableStateOf<List<MediaPost>?>(null); private set
    var feedStart by mutableIntStateOf(0); private set
    var feedToken by mutableIntStateOf(0); private set
    var feedMode by mutableStateOf("live"); private set
    var libraryThread by mutableStateOf("")
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    private var request: Job? = null
    private var generation = 0
    private val catalogs = linkedMapOf<String, List<ThreadCard>>()
    private val positions = mutableMapOf<String, String>()
    private var threadOrder = emptyList<String>()
    var allowAdult by mutableStateOf(preferences.getBoolean("adult", false)); private set
    var preload by mutableStateOf(preferences.getBoolean("preload", true)); private set

    init {
        viewModelScope.launch { app.vault.revision.collect { saved = app.vault.list(); history = app.vault.history() } }
        viewModelScope.launch {
            try { boards = app.api.boards() } catch (e: Exception) { if (e is CancellationException) throw e }
        }
        chooseBoard(board)
    }
    fun adult(value: Boolean) { allowAdult = value; preferences.edit().putBoolean("adult", value).apply() }
    fun preloading(value: Boolean) { preload = value; preferences.edit().putBoolean("preload", value).apply() }
    fun navigate(value: String) { generation++; request?.cancel(); loading = false; error = null; feed = null; tab = value }
    fun chooseBoard(value: String, refresh: Boolean = false) {
        if (!MediaRules.board(value)) return
        preferences.edit().putString("board", value).apply()
        request?.cancel(); board = value; tab = "browse"; feed = null; threads = emptyList(); error = null; loading = true
        val issued = ++generation
        catalogs[value]?.takeIf { !refresh }?.let { threads = it; loading = false; return }
        request = viewModelScope.launch {
            try { threads = app.api.catalog(value); catalogs[value] = threads }
            catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Could not connect to 4chan." }
            finally { if (issued == generation) loading = false }
        }
    }
    fun openThread(thread: ThreadCard) {
        request?.cancel(); loading = true; error = null
        val issued = ++generation
        request = viewModelScope.launch {
            try {
                val media = app.api.thread(thread.board, thread.id)
                if (media.isEmpty()) message = "This thread has no supported media."
                else {
                    setFeed(media, resumeIndex(media), "live")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (feed != null) message = e.message ?: "Could not open this thread."
                else error = e.message ?: "Could not open this thread."
            }
            finally { if (issued == generation) loading = false }
        }
    }
    private fun setFeed(items: List<MediaPost>, at: Int, mode: String) {
        if (items.isEmpty()) return
        if (feed == null || feedMode != mode || feed?.firstOrNull()?.board != items.first().board) {
            threadOrder = groupSavedThreads(if (mode == "saved") saved else history.map { it.post })
                .filter { it.board == items.first().board }.map { it.key }
        }
        feedStart = at.coerceIn(items.indices); feedMode = mode; feedToken++; feed = items
    }
    fun rememberPosition(post: MediaPost) { positions["${post.board}_${post.thread}"] = post.key }
    fun resumeIndex(items: List<MediaPost>): Int {
        val first = items.firstOrNull() ?: return 0
        val last = positions["${first.board}_${first.thread}"] ?: history.firstOrNull { it.post.board == first.board && it.post.thread == first.thread }?.post?.key
        return items.indexOfFirst { it.key == last }.coerceAtLeast(0)
    }
    fun openSaved(items: List<MediaPost>, at: Int = resumeIndex(items)) { setFeed(items, at, "saved") }
    fun openHistory(post: MediaPost) {
        val items = history.filter { it.post.board == post.board && it.post.thread == post.thread }
            .map { h -> saved.firstOrNull { it.key == h.post.key } ?: h.post }.sortedBy { it.post }
        setFeed(items, items.indexOfFirst { it.key == post.key }, "history")
    }
    fun switchThread(current: MediaPost, direction: Int) {
        if (loading) return
        rememberPosition(current)
        if (feedMode == "live") {
            val candidates = threads.filter { it.board == current.board && (it.thumbnail != null || it.images > 0) }
            val at = candidates.indexOfFirst { it.id == current.thread }
            val next = if (at >= 0) candidates.getOrNull(at + direction) else null
            if (next == null) { message = "${if (direction > 0) "Last" else "First"} thread on /${current.board}/."; return }
            openThread(next)
        } else {
            val available = groupSavedThreads(if (feedMode == "saved") saved else history.map { it.post }).associateBy { it.key }
            val candidates = threadOrder.mapNotNull { available[it] }.filter { it.board == current.board }
            val at = candidates.indexOfFirst { it.thread == current.thread }
            val next = if (at >= 0) candidates.getOrNull(at + direction) else null
            if (next == null) { message = "${if (direction > 0) "Last" else "First"} saved thread on /${current.board}/."; return }
            val items = next.files.map { p -> saved.firstOrNull { it.key == p.key } ?: p }
            if (feedMode == "saved") libraryThread = next.key
            setFeed(items, resumeIndex(items), feedMode)
        }
    }
    fun dismissMessage() { message = null }
    fun notify(value: String) { message = value }
    fun save(posts: List<MediaPost>) {
        val new = posts.filter { p -> saved.none { it.key == p.key } }
        if (new.isEmpty()) { message = "Already saved in your library."; return }
        DownloadWorker.enqueue(app, new)
        message = "${new.size} file${if (new.size == 1) "" else "s"} queued. See Library for progress."
    }
    fun watched(post: MediaPost, position: Long = 0, duration: Long = 0) {
        viewModelScope.launch { app.vault.watched(post, position, duration) }
    }
    fun remove(posts: List<MediaPost>, historyOnly: Boolean) {
        viewModelScope.launch {
            try {
                if (historyOnly) app.vault.removeHistory(posts.map { it.key }.toSet())
                else posts.forEach { WorkManager.getInstance(app).cancelUniqueWork("save-${it.key}"); app.vault.delete(it) }
                if (!historyOnly && feed != null) { feed = null; tab = "saved" }
                message = if (historyOnly) "History removed." else "${posts.size} saved files deleted."
            } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message ?: "Could not delete these files." }
        }
    }
}
