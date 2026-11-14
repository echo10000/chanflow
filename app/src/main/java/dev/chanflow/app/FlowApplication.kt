package dev.chanflow.app

import android.app.Application
import android.os.Build
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FlowApplication : Application(), ImageLoaderFactory {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    val api by lazy { ChanApi(http) }
    val vault by lazy { VaultStore(this) }
    private val videoCache by lazy {
        SimpleCache(File(cacheDir, "video"), LeastRecentlyUsedCacheEvictor(128L * 1024 * 1024), StandaloneDatabaseProvider(this))
    }
    val mediaSources by lazy {
        CacheDataSource.Factory().setCache(videoCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, OkHttpDataSource.Factory(http)))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    override fun newImageLoader() = ImageLoader.Builder(this).okHttpClient(http)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.16).build() }
        .diskCache { DiskCache.Builder().directory(File(cacheDir, "images")).maxSizeBytes(96L * 1024 * 1024).build() }
        .crossfade(false).build()
}
