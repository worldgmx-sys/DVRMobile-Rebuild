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
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
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

    private suspend fun command(urlForIp: (String) -> String): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            val req = Request.Builder().url(urlForIp(ip)).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }
    }
}
