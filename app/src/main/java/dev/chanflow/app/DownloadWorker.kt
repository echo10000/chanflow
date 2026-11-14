package dev.chanflow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.IOException

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as FlowApplication
        val post = runCatching { MediaPost.fromJson(inputData.getString("media") ?: "") }.getOrNull()
            ?: return@withContext Result.failure(workDataOf("error" to "Missing media details"))
        if (!MediaRules.valid(post) || !MediaRules.validMediaUrl(post.url))
            return@withContext Result.failure(workDataOf("error" to "Unsupported media address"))
        if (app.vault.contains(post)) return@withContext Result.success()
        val destination = app.vault.file(post)
        val partial = File(destination.path + ".part")
        downloadSlots.withPermit { try {
            setForeground(notification(post, 0))
            val request = Request.Builder().url(post.url).header("User-Agent", "ChanFlow/${BuildConfig.VERSION_NAME}").build()
            app.http.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext Result.failure(workDataOf("error" to "Media expired before it could be saved"))
                if (!response.isSuccessful) throw IOException("Download returned ${response.code}")
                val body = response.body ?: throw IOException("Empty download")
                val total = body.contentLength()
                if (total > MediaRules.MAX_BYTES) return@withContext Result.failure(workDataOf("error" to "File exceeds the 250 MB limit"))
                var written = 0L
                var lastProgress = 0L
                body.byteStream().use { source -> partial.outputStream().use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > MediaRules.MAX_BYTES) throw IOException("File exceeds the 250 MB limit")
                        target.write(buffer, 0, count)
                        val now = System.currentTimeMillis()
                        if (now - lastProgress > 700) {
                            setProgress(workDataOf("bytes" to written, "total" to total, "name" to post.filename))
                            setForeground(notification(post, if (total > 0) (written * 100 / total).toInt() else 0))
                            lastProgress = now
                        }
                    }
                } }
                if (written == 0L || (total >= 0 && written != total)) throw IOException("The download was incomplete")
            }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(destination)) { "Could not finish saving the file" }
            app.vault.record(post)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (runAttemptCount < 2 && e is IOException) Result.retry()
            else Result.failure(workDataOf("error" to (e.message ?: "Download failed")))
        } finally { partial.delete() } }
    }

    private fun notification(post: MediaPost, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("downloads", "Saved media", NotificationManager.IMPORTANCE_LOW))
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notice = NotificationCompat.Builder(applicationContext, "downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Saving to ChanFlow").setContentText(post.filename.take(60))
            .setProgress(100, percent, percent == 0).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancel).build()
        val notificationId = post.key.hashCode() and Int.MAX_VALUE
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notice)
    }

    companion object {
        private val downloadSlots = Semaphore(2)
        fun enqueue(context: Context, posts: List<MediaPost>) {
            val manager = WorkManager.getInstance(context)
            // Independent unique jobs prevent duplicates and isolate failures.
            val requests = posts.distinctBy { it.key }.filter(MediaRules::valid).map { post ->
                OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("media" to post.json()))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .addTag("downloads").addTag(post.key).build().let { post.key to it }
            }
            requests.forEach { (key, request) ->
                manager.enqueueUniqueWork("save-$key", ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
