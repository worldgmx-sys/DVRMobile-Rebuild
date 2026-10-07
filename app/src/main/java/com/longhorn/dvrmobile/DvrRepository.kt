package com.longhorn.dvrmobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DvrRepository(context: Context) {
    private val discovery = DvrDiscovery(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val status: StateFlow<DvrStatus> = discovery.status

    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()
    fun manualIp(ip: String) = discovery.setManualIp(ip)

    suspend fun probe(): Result<String> = command { DvrProtocol.getDvr(it) }
    suspend fun startRecording() = command { DvrProtocol.video(it, "normal") }
    suspend fun stopRecording() = command { DvrProtocol.video(it, "stop") }
    suspend fun capture() = command { DvrProtocol.video(it, "capture") }
    suspend fun eventRecording() = command { DvrProtocol.video(it, "event") }
    suspend fun setMic(on: Boolean) = command { DvrProtocol.setMic(it, on) }
    suspend fun setDvr(on: Boolean) = command { DvrProtocol.setDvr(it, on) }
    suspend fun formatSd() = command { DvrProtocol.formatSd(it) }

    suspend fun listMedia(kind: DvrMediaFile.Kind): Result<List<DvrMediaFile>> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        val property = when (kind) {
            DvrMediaFile.Kind.NORMAL -> "DCIM"
            DvrMediaFile.Kind.EVENT -> "EVENT"
            DvrMediaFile.Kind.PHOTO -> "PHOTO"
        }
        runCatching {
            val body = get(DvrProtocol.list(ip, property))
            parseMediaList(body, kind)
        }
    }

    fun mediaUrl(file: DvrMediaFile): String? =
        status.value.ip?.let { DvrProtocol.file(it, file.remotePath) }

    fun thumbnailUrl(file: DvrMediaFile): String? =
        status.value.ip?.let { DvrProtocol.thumbnail(it, file.remotePath) }

    private suspend fun command(urlForIp: (String) -> String): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching { get(urlForIp(ip)) }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).get().build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    private fun parseMediaList(raw: String, kind: DvrMediaFile.Kind): List<DvrMediaFile> {
        val base = when (kind) {
            DvrMediaFile.Kind.NORMAL -> "/mnt/mmc/Normal/"
            DvrMediaFile.Kind.EVENT -> "/mnt/mmc/Event/"
            DvrMediaFile.Kind.PHOTO -> "/mnt/mmc/Photo/"
        }

        val allowed = when (kind) {
            DvrMediaFile.Kind.PHOTO -> listOf(".jpg", ".jpeg", ".png")
            else -> listOf(".mp4", ".mov", ".ts", ".avi")
        }

        val normalized = raw
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u002F", "/")

        val fullPathRegex = Regex("""/mnt/mmc/(?:Normal|Event|Photo)/[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
        val paths = linkedSetOf<String>()

        fullPathRegex.findAll(normalized).forEach { match ->
            val p = match.value.trimEnd(',', ';', ')', ']', '}')
            if (allowed.any { p.lowercase().endsWith(it) }) paths += p
        }

        if (paths.isEmpty()) {
            val fileRegex = Regex("""[A-Za-z0-9_().\-]+\.(?:mp4|mov|ts|avi|jpe?g|png)""", RegexOption.IGNORE_CASE)
            fileRegex.findAll(normalized).forEach { match ->
                val name = match.value
                if (allowed.any { name.lowercase().endsWith(it) }) {
                    paths += base + name
                }
            }
        }

        return paths
            .map { path ->
                DvrMediaFile(
                    remotePath = path,
                    name = path.substringAfterLast('/'),
                    kind = kind,
                )
            }
            .sortedByDescending { it.name }
    }
}
