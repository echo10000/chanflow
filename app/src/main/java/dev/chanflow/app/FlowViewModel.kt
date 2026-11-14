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
    var boards by mutableStateOf<List<Board>>(emptyList()); private set
    var threads by mutableStateOf<List<ThreadCard>>(emptyList()); private set
    var saved by mutableStateOf<List<MediaPost>>(emptyList()); private set
    var history by mutableStateOf<List<Watch>>(emptyList()); private set
    var board by mutableStateOf("w"); private set
    var tab by mutableStateOf("browse"); private set
    var feed by mutableStateOf<List<MediaPost>?>(null); private set
    var feedStart by mutableIntStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    private var request: Job? = null
    private var generation = 0
    private val preferences = application.getSharedPreferences("settings", 0)
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
    fun chooseBoard(value: String) {
        if (!MediaRules.board(value)) return
        request?.cancel(); board = value; tab = "browse"; feed = null; threads = emptyList(); error = null; loading = true
        val issued = ++generation
        request = viewModelScope.launch {
            try { threads = app.api.catalog(value) }
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
                    val last = history.firstOrNull { it.post.board == thread.board && it.post.thread == thread.id }?.post?.key
                    feedStart = media.indexOfFirst { it.key == last }.coerceAtLeast(0)
                    feed = media
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Could not open this thread." }
            finally { if (issued == generation) loading = false }
        }
    }
    fun openSaved(items: List<MediaPost>, at: Int) { feedStart = at; feed = items }
    fun openHistory(at: Int) { feedStart = at; feed = history.map { h -> saved.firstOrNull { it.key == h.post.key } ?: h.post } }
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
