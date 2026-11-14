package dev.chanflow.app

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChanApi(private val client: OkHttpClient) {
    private val gate = Mutex()
    private var lastRequest = 0L
    private val cached = LinkedHashMap<String, Pair<Long, String>>()

    // 4chan asks clients to make at most one API request per second, and not
    // refresh an individual thread faster than once every ten seconds.
    private suspend fun json(path: String, ttl: Long = 30_000): String = withContext(Dispatchers.IO) {
        gate.withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            cached[path]?.takeIf { now - it.first < ttl }?.let { return@withLock it.second }
            delay((1000 - (now - lastRequest)).coerceAtLeast(0))
            lastRequest = android.os.SystemClock.elapsedRealtime()
            val request = Request.Builder().url("https://a.4cdn.org/$path")
                .header("User-Agent", "ChanFlow/${BuildConfig.VERSION_NAME} (Android reader)").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(when (response.code) {
                    404 -> "This thread has expired or was removed. Saved files still work offline."
                    429 -> "4chan is limiting requests. Wait a moment and try again."
                    else -> "4chan returned ${response.code}. Try again shortly."
                })
                val body = response.body ?: throw IOException("4chan returned an empty response.")
                if (body.contentLength() > 8 * 1024 * 1024) throw IOException("Response is too large.")
                val bytes = body.byteStream().use { stream ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (buffer.size() <= 8 * 1024 * 1024) {
                        val n = stream.read(chunk)
                        if (n < 0) break
                        buffer.write(chunk, 0, n)
                    }
                    buffer.toByteArray()
                }
                if (bytes.size > 8 * 1024 * 1024) throw IOException("Response is too large.")
                val text = bytes.toString(Charsets.UTF_8)
                if (cached.size >= 24) cached.remove(cached.keys.first())
                cached[path] = android.os.SystemClock.elapsedRealtime() to text
                text
            }
        }
    }

    suspend fun boards(): List<Board> = withContext(Dispatchers.IO) {
        val rows = JSONObject(json("boards.json", 3_600_000)).getJSONArray("boards")
        (0 until rows.length()).map { rows.getJSONObject(it) }.map {
            Board(it.getString("board"), plain(it.optString("title")), it.optInt("ws_board") == 1)
        }.filter { MediaRules.board(it.id) }
    }

    suspend fun catalog(board: String): List<ThreadCard> = withContext(Dispatchers.IO) {
        require(MediaRules.board(board))
        val pages = JSONArray(json("$board/catalog.json", 60_000))
        buildList {
            for (p in 0 until pages.length()) {
                val threads = pages.getJSONObject(p).getJSONArray("threads")
                for (i in 0 until threads.length()) {
                    val t = threads.getJSONObject(i)
                    val text = plain(t.optString("com"))
                    add(ThreadCard(board, t.getLong("no"), plain(t.optString("sub")).ifBlank {
                        text.lineSequence().firstOrNull()?.take(100).orEmpty().ifBlank { "Thread #${t.getLong("no")}" }
                    }, text, if (t.has("tim")) "https://i.4cdn.org/$board/${t.getLong("tim")}s.jpg" else null,
                        t.optInt("replies"), t.optInt("images")))
                }
            }
        }
    }

    suspend fun thread(board: String, thread: Long): List<MediaPost> = withContext(Dispatchers.IO) {
        require(MediaRules.board(board) && thread > 0)
        val posts = JSONObject(json("$board/thread/$thread.json")).getJSONArray("posts")
        val op = posts.getJSONObject(0)
        val title = plain(op.optString("sub")).ifBlank { plain(op.optString("com")).take(100) }
            .ifBlank { "Thread #$thread" }
        buildList {
            for (i in 0 until posts.length()) {
                val p = posts.getJSONObject(i)
                if (!p.has("tim") || p.optInt("filedeleted") == 1) continue
                val media = MediaPost(board, thread, p.getLong("no"), p.getLong("tim"),
                    p.optString("ext"), plain(p.optString("filename")), title,
                    plain(p.optString("com")).take(1200), p.optInt("w"), p.optInt("h"), p.optLong("fsize"))
                if (MediaRules.valid(media)) add(media)
            }
        }
    }

    private fun plain(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace('\u00a0', ' ').trim()
}
