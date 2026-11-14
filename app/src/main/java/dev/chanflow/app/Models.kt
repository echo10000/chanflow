package dev.chanflow.app

import org.json.JSONObject
import java.net.URI

data class Board(val id: String, val title: String, val safe: Boolean)
data class ThreadCard(val board: String, val id: Long, val title: String, val caption: String,
                      val thumbnail: String?, val replies: Int, val images: Int)

data class MediaPost(
    val board: String, val thread: Long, val post: Long, val timestamp: Long,
    val extension: String, val filename: String, val title: String, val caption: String,
    val width: Int = 0, val height: Int = 0, val bytes: Long = 0,
    val localPath: String = "", val savedAt: Long = 0
) {
    val key: String get() = "${board}_$post"
    val url: String get() = "https://i.4cdn.org/$board/$timestamp$extension"
    val thumbnail: String get() = "https://i.4cdn.org/$board/${timestamp}s.jpg"
    val video: Boolean get() = extension == ".webm" || extension == ".mp4"
    fun json(): String = JSONObject().put("board", board).put("thread", thread).put("post", post)
        .put("timestamp", timestamp).put("extension", extension).put("filename", filename)
        .put("title", title).put("caption", caption.take(1200)).put("width", width)
        .put("height", height).put("bytes", bytes).put("localPath", localPath).put("savedAt", savedAt).toString()

    companion object {
        fun fromJson(value: String): MediaPost {
            val j = JSONObject(value)
            return MediaPost(j.getString("board"), j.getLong("thread"), j.getLong("post"),
                j.getLong("timestamp"), j.getString("extension"), j.optString("filename"),
                j.optString("title"), j.optString("caption"), j.optInt("width"), j.optInt("height"),
                j.optLong("bytes"), j.optString("localPath"), j.optLong("savedAt"))
        }
    }
}

object MediaRules {
    const val MAX_BYTES = 250L * 1024 * 1024
    val extensions = setOf(".jpg", ".jpeg", ".png", ".gif", ".webm", ".mp4")
    fun board(value: String) = value.matches(Regex("[a-z0-9]{1,12}"))
    fun valid(post: MediaPost): Boolean = board(post.board) && post.thread > 0 && post.post > 0 &&
        post.timestamp > 0 && post.extension in extensions && post.bytes in 0..MAX_BYTES
    fun validMediaUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "i.4cdn.org" && uri.port == -1 &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.matches(Regex("/[a-z0-9]{1,12}/[0-9]+(s\\.jpg|\\.(jpg|jpeg|png|gif|webm|mp4))"))
    } catch (_: Exception) { false }
}
