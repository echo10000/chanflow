package dev.chanflow.app

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object LookAhead {
    private val executor = Executors.newSingleThreadExecutor()
    suspend fun prepare(app: FlowApplication, next: MediaPost) = suspendCancellableCoroutine<Unit> { continuation ->
        val writer = CacheWriter(app.mediaSources.createDataSource(), DataSpec.Builder()
            .setUri(Uri.parse(next.url)).setLength(minOf(5L * 1024 * 1024, next.bytes.takeIf { it > 0 } ?: (5L * 1024 * 1024))).build(), null, null)
        continuation.invokeOnCancellation { writer.cancel() }
        executor.execute {
            try { if (continuation.isActive) writer.cache() } catch (_: Exception) { /* Best effort. */ }
            finally { if (continuation.isActive) continuation.resume(Unit) }
        }
    }
}
