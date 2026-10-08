package com.longhorn.dvr.worldgm

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class FirmwareKind { SOC, MCU }

class DvrRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences("dvr_mobile", Context.MODE_PRIVATE)
    private val discovery = DvrDiscovery(appContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val status: StateFlow<DvrStatus> = discovery.status

    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()
    fun manualIp(ip: String) = discovery.setManualIp(ip)

    fun downloadTreeUri(): String? = prefs.getString("download_tree_uri", null)

    fun setDownloadTreeUri(uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove("download_tree_uri") else putString("download_tree_uri", uri)
        }.apply()
    }

    suspend fun probe(): Result<String> = command { DvrProtocol.getDvr(it) }
    suspend fun startRecording() = command { DvrProtocol.video(it, "normal") }
    suspend fun stopRecording() = command { DvrProtocol.video(it, "stop") }
    suspend fun eventRecording() = command { DvrProtocol.video(it, "event") }
    suspend fun setMic(on: Boolean) = command { DvrProtocol.setMic(it, on) }
    suspend fun setDvr(on: Boolean) = command { DvrProtocol.setDvr(it, on) }
    suspend fun removeSd() = command { DvrProtocol.removeSd(it) }
    suspend fun formatSd() = command { DvrProtocol.formatSd(it) }
    suspend fun authorizeApp(value: String) = command { DvrProtocol.setApp(it, value) }
    suspend fun setAuthTime(value: String) = command { DvrProtocol.setAuthTime(it, value) }

    suspend fun initializeClient(versionCode: Long): Result<String> =
        command { DvrProtocol.setApp(it, versionCode.toString()) }

    suspend fun aiActiveTest(): Result<Boolean> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            val raw = get(DvrProtocol.aiActiveTest(ip))
            DvrResponseParser.ppgInt(raw) == 1
        }
    }

    suspend fun setAlgEnabled(enabled: Boolean): Result<String> =
        command { DvrProtocol.setAlgEnabled(it, enabled) }

    suspend fun setParkingMode(mode: ParkingMode): Result<String> =
        command { DvrProtocol.setParkingModeAStar(it, mode.value) }

    suspend fun setParkingGSensor(level: Int): Result<String> {
        require(level in 0..3) { "停车 G-sensor 档位必须为 0..3" }
        return command { DvrProtocol.setParkingGSensorAStar(it, level) }
    }

    suspend fun getPeopleRoi(): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            val raw = get(DvrProtocol.getPeopleRoi(ip))
            DvrResponseParser.normalizeRoi(raw) ?: error("设备未返回可识别的 ROI")
        }
    }

    suspend fun setPeopleRoi(roi: String): Result<String> {
        require(DvrResponseParser.isValidRoi(roi)) {
            "ROI 需为 4~6 个 1920×1080 像素坐标点，例如 0,1080;0,0;1920,0;1920,1080"
        }
        return command { DvrProtocol.setPeopleRoi(it, roi) }
    }

    suspend fun setPeopleDetectDuration(value: Int): Result<String> {
        require(value in 0..120) { "人员检测持续时间参数必须为 0..120" }
        return command { DvrProtocol.setPeopleDetectDuration(it, value) }
    }

    suspend fun setSigmaParkingMonitor(enabled: Boolean): Result<String> =
        command { DvrProtocol.setParkingMonitor(it, enabled) }

    suspend fun setSigmaGSensor(value: String): Result<String> {
        require(value in setOf("OFF", "LEVEL0", "LEVEL1", "LEVEL2", "LEVEL3", "LEVEL4")) {
            "无效的行车 G-sensor 档位"
        }
        return command { DvrProtocol.setGSensor(it, value) }
    }

    suspend fun setSigmaPowerOnGSensor(value: String): Result<String> {
        require(value in setOf("OFF", "LEVEL0", "LEVEL1", "LEVEL2")) {
            "无效的停车唤醒 G-sensor 档位"
        }
        return command { DvrProtocol.setPowerOnGSensor(it, value) }
    }


    suspend fun setSigmaProperty(property: String, value: String): Result<String> {
        val allowed = setOf(
            "VideoRes", "LoopingVideo", "MotionDetect", "MotionVideoTime",
            "LDWS", "FCWS", "SAG", "NightMode", "WNR", "HDR",
            "SlowMotion", "Timelapse", "AutoRec", "VideoPreRecord",
            "MicSensitivity", "VideoQuality", "VoiceSwitch", "Flicker",
            "ISO", "AWB", "EV", "DateLogoStamp", "GpsStamp", "SpeedStamp",
            "Brightness", "Contrast", "Saturation", "Sharpness"
        )
        require(property in allowed) { "不允许的 SigmaStar 参数：$property" }
        return command { DvrProtocol.configSet(it, property, value) }
    }

    suspend fun captureVerified(): Result<DvrMediaFile?> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))

        suspend fun photoSnapshot(): Set<String> =
            listMedia(DvrMediaFile.Kind.PHOTO).getOrDefault(emptyList())
                .map { it.remotePath }
                .toSet()

        suspend fun waitForNewPhoto(before: Set<String>): DvrMediaFile? {
            repeat(5) {
                delay(700)
                val files = listMedia(DvrMediaFile.Kind.PHOTO).getOrDefault(emptyList())
                files.firstOrNull { it.remotePath !in before }?.let { return it }
            }
            return null
        }

        runCatching {
            val before = photoSnapshot()
            get(DvrProtocol.video(ip, "capture"))
            waitForNewPhoto(before)?.let { return@runCatching it }

            get(DvrProtocol.setDvr(ip, true))
            delay(400)
            get(DvrProtocol.video(ip, "capture"))
            waitForNewPhoto(before)
        }
    }

    suspend fun listMedia(kind: DvrMediaFile.Kind): Result<List<DvrMediaFile>> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))

        val property = when (kind) {
            DvrMediaFile.Kind.NORMAL -> "DCIM"
            DvrMediaFile.Kind.EVENT -> "EVENT"
            DvrMediaFile.Kind.PARKING -> "DCIM"
            DvrMediaFile.Kind.PHOTO -> "PHOTO"
        }

        runCatching {
            val body = get(DvrProtocol.list(ip, property))
            parseMediaList(body, kind)
        }
    }

    suspend fun deleteMedia(file: DvrMediaFile): Result<String> =
        command { DvrProtocol.delete(it, file.remotePath) }

    suspend fun moveMedia(file: DvrMediaFile): Result<String> =
        command { DvrProtocol.move(it, file.remotePath) }

    fun mediaUrl(file: DvrMediaFile): String? =
        status.value.ip?.let { DvrProtocol.file(it, file.remotePath) }

    fun thumbnailUrl(file: DvrMediaFile): String? =
        status.value.ip?.let { DvrProtocol.thumbnail(it, file.remotePath) }

    suspend fun download(
        file: DvrMediaFile,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = mediaUrl(file) ?: error("尚未发现记录仪")
            val tree = downloadTreeUri()?.let(Uri::parse)
                ?: error("请先在设置中选择下载目录")
            val root = DocumentFile.fromTreeUri(appContext, tree)
                ?: error("无法访问所选目录")
            if (!root.canWrite()) error("所选目录没有写入权限")

            val mime = mimeFor(file.name)
            val target = root.findFile(file.name)
                ?: root.createFile(mime, file.name)
                ?: error("无法创建目标文件")

            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("服务器未返回文件内容")
                val total = body.contentLength()
                resolver.openOutputStream(target.uri, "wt").use { output ->
                    if (output == null) error("无法打开目标文件")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0L
                        onProgress(0L, total)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            }
            target.uri.toString()
        }
    }

    suspend fun uploadFirmware(uriString: String, kind: FirmwareKind): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            val uri = Uri.parse(uriString)
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取固件文件")

            when (kind) {
                FirmwareKind.SOC -> get(DvrProtocol.prepareSocUpgrade(ip))
                FirmwareKind.MCU -> get(DvrProtocol.prepareMcuUpgrade(ip))
            }
            delay(1000)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "uploadfile",
                    "firmware.bin",
                    bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(DvrProtocol.upload(ip))
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }
    }

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

    private fun mimeFor(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".mov", true) -> "video/quicktime"
        name.endsWith(".avi", true) -> "video/x-msvideo"
        name.endsWith(".ts", true) -> "video/mp2t"
        else -> "application/octet-stream"
    }

    private fun parseMediaList(raw: String, kind: DvrMediaFile.Kind): List<DvrMediaFile> {
        val base = when (kind) {
            DvrMediaFile.Kind.NORMAL -> "/mnt/mmc/Normal/"
            DvrMediaFile.Kind.EVENT -> "/mnt/mmc/Event/"
            DvrMediaFile.Kind.PARKING -> "/mnt/mmc/Parking/"
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

        val fullPathRegex = Regex(
            """/mnt/mmc/(?:Normal|Event|Parking|Photo)/[^\s"'<>\\]+""",
            RegexOption.IGNORE_CASE
        )
        val paths = linkedSetOf<String>()

        fullPathRegex.findAll(normalized).forEach { match ->
            val p = match.value.trimEnd(',', ';', ')', ']', '}')
            val categoryMatches = when (kind) {
                DvrMediaFile.Kind.NORMAL -> p.contains("/Normal/", true)
                DvrMediaFile.Kind.EVENT -> p.contains("/Event/", true)
                DvrMediaFile.Kind.PARKING -> p.contains("/Parking/", true)
                DvrMediaFile.Kind.PHOTO -> p.contains("/Photo/", true)
            }
            if (categoryMatches && allowed.any { p.lowercase().endsWith(it) }) paths += p
        }

        if (paths.isEmpty() && kind != DvrMediaFile.Kind.PARKING) {
            val fileRegex = Regex(
                """[A-Za-z0-9_().\-]+\.(?:mp4|mov|ts|avi|jpe?g|png)""",
                RegexOption.IGNORE_CASE
            )
            fileRegex.findAll(normalized).forEach { match ->
                val name = match.value
                if (allowed.any { name.lowercase().endsWith(it) }) paths += base + name
            }
        }

        return paths.map { path ->
            DvrMediaFile(
                remotePath = path,
                name = path.substringAfterLast('/'),
                kind = kind,
            )
        }.sortedByDescending { it.name }
    }
}
