package dev.chanflow.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AppUpdates {
    data class Release(val version: String, val url: String)
    suspend fun latest(app: FlowApplication): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "ChanFlow/${BuildConfig.VERSION_NAME}").build()
        app.http.newCall(request).execute().use { response ->
            if (response.code == 404) throw IOException("No public release has been published yet.")
            if (!response.isSuccessful) throw IOException("GitHub returned ${response.code}. Try again later.")
            val info = JSONObject(response.body?.string() ?: throw IOException("Empty update response"))
            val tag = info.getString("tag_name")
            if (!Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(tag)) throw IOException("Unsupported release version")
            if (compareVersions(tag.removePrefix("v"), BuildConfig.VERSION_NAME.substringBefore('-')) <= 0) return@withContext null
            val assets = info.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") != "chanflow.apk") continue
                val url = a.getString("browser_download_url")
                val expected = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/$tag/chanflow.apk"
                if (url != expected) throw IOException("The release asset address is unexpected.")
                return@withContext Release(tag.removePrefix("v"), url)
            }
            throw IOException("This release has no Android APK yet.")
        }
    }
    internal fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val diff = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
    suspend fun download(app: FlowApplication, release: Release): File = withContext(Dispatchers.IO) {
        val folder = File(app.cacheDir, "updates").apply { mkdirs() }
        val partial = File(folder, "update.part")
        val apk = File(folder, "chanflow.apk")
        val http = app.http.newBuilder().followRedirects(true).followSslRedirects(false).callTimeout(5, TimeUnit.MINUTES).build()
        try {
            http.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Update download failed (${response.code}).")
                val host = response.request.url.host
                if (host != "github.com" && !host.endsWith(".githubusercontent.com")) throw IOException("Unexpected update download host.")
                val body = response.body ?: throw IOException("Empty update download")
                val length = body.contentLength()
                if (length > 100L * 1024 * 1024) throw IOException("Update exceeds the size limit.")
                var total = 0L
                body.byteStream().use { input -> partial.outputStream().use { out ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        total += n
                        if (total > 100L * 1024 * 1024) throw IOException("Update exceeds the size limit.")
                        out.write(bytes, 0, n)
                    }
                } }
                if (total == 0L || (length >= 0 && total != length)) throw IOException("Update download was incomplete.")
            }
            verify(app, partial)
            check(partial.renameTo(apk)) { "Could not prepare the update" }
            apk
        } finally { partial.delete() }
    }
    @Suppress("DEPRECATION")
    private fun verify(context: Context, file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val downloaded = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("The downloaded file is not a valid APK.")
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (downloaded.packageName != context.packageName) throw IOException("This update is for a different app. Preview builds use a separate package.")
        val incoming = if (Build.VERSION.SDK_INT >= 28) downloaded.signingInfo?.apkContentsSigners else downloaded.signatures
        val current = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        if (incoming.isNullOrEmpty() || current.isNullOrEmpty() || incoming.map { it.toCharsString() }.toSet() != current.map { it.toCharsString() }.toSet())
            throw IOException("The update was not signed by this app’s release key.")
        val newCode = if (Build.VERSION.SDK_INT >= 28) downloaded.longVersionCode else downloaded.versionCode.toLong()
        val oldCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        if (newCode <= oldCode) throw IOException("This APK is not a newer version.")
    }
    fun install(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                Toast.makeText(context, "Allow updates from ChanFlow, return here, then tap Install again.", Toast.LENGTH_LONG).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { Toast.makeText(context, "Could not open the Android installer.", Toast.LENGTH_LONG).show() }
    }
}
