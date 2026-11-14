package dev.chanflow.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class Watch(val post: MediaPost, val position: Long, val duration: Long, val watchedAt: Long)

class VaultStore(context: Context) : SQLiteOpenHelper(context, "vault.db", null, 2) {
    val directory = File(context.filesDir, "vault").apply { mkdirs() }
    val revision = MutableStateFlow(0L)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE media (id TEXT PRIMARY KEY, metadata TEXT NOT NULL, saved_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX media_recent ON media(saved_at DESC)")
        createHistory(db)
    }
    private fun createHistory(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id TEXT PRIMARY KEY, metadata TEXT NOT NULL, position INTEGER NOT NULL, duration INTEGER NOT NULL, watched_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) createHistory(db) }

    suspend fun history(): List<Watch> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT metadata,position,duration,watched_at FROM history ORDER BY watched_at DESC LIMIT 200", null).use { rows ->
            buildList { while (rows.moveToNext()) {
                val post = runCatching { MediaPost.fromJson(rows.getString(0)) }.getOrNull() ?: continue
                add(Watch(post, rows.getLong(1), rows.getLong(2), rows.getLong(3)))
            } }
        }
    }
    suspend fun watched(post: MediaPost, position: Long = 0, duration: Long = 0) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("history", null, ContentValues().apply {
            put("id", post.key); put("metadata", post.copy(localPath = "").json()); put("position", position)
            put("duration", duration); put("watched_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY watched_at DESC LIMIT 200)")
        revision.value += 1
    }
    suspend fun removeHistory(keys: Set<String>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try { keys.forEach { db.delete("history", "id=?", arrayOf(it)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
        revision.value += 1
    }

    suspend fun list(): List<MediaPost> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT metadata FROM media ORDER BY saved_at DESC", null).use { rows ->
            buildList { while (rows.moveToNext()) {
                val post = runCatching { MediaPost.fromJson(rows.getString(0)) }.getOrNull() ?: continue
                if (file(post).isFile) add(post)
            } }
        }
    }
    fun file(post: MediaPost): File {
        require(MediaRules.valid(post))
        return File(directory, "${post.key}${post.extension}")
    }
    suspend fun contains(post: MediaPost): Boolean = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT 1 FROM media WHERE id=?", arrayOf(post.key)).use { it.moveToFirst() } && file(post).isFile
    }
    suspend fun record(post: MediaPost) = withContext(Dispatchers.IO) {
        val saved = post.copy(localPath = file(post).absolutePath, savedAt = System.currentTimeMillis())
        writableDatabase.insertWithOnConflict("media", null, ContentValues().apply {
            put("id", saved.key); put("metadata", saved.json()); put("saved_at", saved.savedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        revision.value += 1
    }
    suspend fun delete(post: MediaPost) = withContext(Dispatchers.IO) {
        val local = file(post)
        check(!local.exists() || local.delete()) { "Could not delete this file. Try again." }
        writableDatabase.delete("media", "id=?", arrayOf(post.key))
        revision.value += 1
    }
}
