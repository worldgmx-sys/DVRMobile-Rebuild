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
            check(DvrResponseParser.isPpgResponse(raw, 9023)) { "9023 未返回有效 PPG 命令响应" }
            DvrResponseParser.ppgInt(raw) == 1
        }
    }

    suspend fun setAlgEnabled(enabled: Boolean): Result<String> =
        ppgCommand(9096) { DvrProtocol.setAlgEnabled(it, enabled) }

    suspend fun setParkingMode(mode: ParkingMode): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            val supported = getSupportedPpgCommands(ip)
            if (supported.isNotEmpty() && 9137 !in supported) {
                error("设备未声明支持 PPG 9137 停车模式")
            }

            check(isPpgReachable(ip)) { "PPG 命令处理器未确认，禁止写入 Sentinel" }
            val setRaw = get(DvrProtocol.setParkingModeAStar(ip, mode.value))
            ensureCommandExecuted(setRaw, 9137)
            // Do not blindly send cmd=3021 until the PPG endpoint is verified.
            delay(250)

            val readBack = readPpgInt(ip, 9137)
            if (readBack != null && readBack != mode.value) {
                error("停车模式写入后读回为 $readBack，期望 ${mode.value}")
            }

            if (readBack == null) {
                "$setRaw\n[warning] 设置命令已发送，但设备没有提供 9137 可读状态"
            } else {
                "$setRaw\n[verified] 9137=$readBack"
            }
        }
    }

    suspend fun setParkingGSensor(level: Int): Result<String> {
        require(level in 0..3) { "停车 G-sensor 档位必须为 0..3" }
        return ppgCommand(9106) { DvrProtocol.setParkingGSensorAStar(it, level) }
    }

    suspend fun getPeopleRoi(): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            val supported = getSupportedPpgCommands(ip)
            if (supported.isNotEmpty() && 9098 !in supported) {
                error("设备未声明支持 PPG 9098 ROI 读取")
            }
            val raw = get(DvrProtocol.getPeopleRoi(ip))
            DvrResponseParser.normalizeRoi(raw)
                ?: error("9098 返回无法解析为 ROI：${raw.take(240)}")
        }
    }

    suspend fun setPeopleRoi(roi: String): Result<String> = withContext(Dispatchers.IO) {
        require(DvrResponseParser.isValidRoi(roi)) {
            "ROI 需为 4~6 个 1920×1080 像素坐标点，例如 0,1080;0,0;1920,0;1920,1080"
        }
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))

        runCatching {
            val supported = getSupportedPpgCommands(ip)
            if (supported.isNotEmpty() && 9097 !in supported) {
                error("设备未声明支持 PPG 9097 ROI 设置")
            }

            check(isPpgReachable(ip)) { "PPG 命令处理器未确认，禁止写入 ROI" }
            val setRaw = get(DvrProtocol.setPeopleRoi(ip, roi))
            ensureCommandExecuted(setRaw, 9097)
            runCatching { get(DvrProtocol.ppgSaveSettings(ip)) }
            delay(250)

            if (supported.isEmpty() || 9098 in supported) {
                val readRaw = runCatching { get(DvrProtocol.getPeopleRoi(ip)) }.getOrNull()
                val readBack = readRaw?.let(DvrResponseParser::normalizeRoi)
                if (readBack != null && readBack != roi) {
                    error("ROI 写入后读回不一致：$readBack")
                }
                if (readBack == null) {
                    return@runCatching "$setRaw\n[warning] ROI 写入命令已发送，但设备无法读回验证"
                }
            }

            "$setRaw\n[verified] ROI=$roi"
        }
    }

    suspend fun setPeopleDetectDuration(value: Int): Result<String> {
        require(value in 0..120) { "人员检测持续时间参数必须为 0..120" }
        return ppgCommand(9099) { DvrProtocol.setPeopleDetectDuration(it, value) }
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


    suspend fun getSigmaProperty(property: String): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            // Firmware GET() names differ from SET() names for these properties.
            val getter = when (property) {
                "VideoRes" -> "Videores"
                "LoopingVideo" -> "VideoClipTime"
                else -> property
            }
            val raw = get(DvrProtocol.configGet(ip, getter))
            parseConfigValue(raw, getter) ?: error("设备未返回可识别的 $property (get=$getter)")
        }
    }

    suspend fun setSigmaProperty(property: String, value: String): Result<String> {
        val allowed = setOf(
            "VideoRes", "LoopingVideo", "VideoQuality", "setbitrate", "AutoRec",
            "VideoPreRecord", "Timelapse", "SlowMotion", "VideoOffTime",
            "ImageRes", "StillBurstShot", "Brightness", "Contrast", "Hue",
            "Saturation", "Sharpness", "Gamma", "EV", "AE", "ISO",
            "Effect", "Flicker", "AWB", "Shutter", "HDR", "NightMode",
            "SoundRecord", "MicSensitivity", "WNR", "PlaybackVolume", "Beep", "VoiceSwitch",
            "ParkingMonitor", "GSensor", "MotionDetect", "MotionVideoTime",
            "LDWS", "FCWS", "SAG", "GpsStamp", "SpeedStamp", "RecStamp",
            "DateLogoStamp", "DateTimeFormat", "SpeedUint", "SpeedCamAlert", "SpeedLimitAlert",
            "Language", "LCDBrightness", "LcdPowerSave", "AutoPowerOff",
            "UsbFunction", "TimeZone", "SyncTime", "TimeSettings"
        )
        require(property in allowed) { "不允许的 SigmaStar 参数：$property" }
        return command { DvrProtocol.configSet(it, property, value) }
    }

    suspend fun syncAdvancedSettings(current: AdvancedDvrState): Result<AdvancedDvrState> =
        withContext(Dispatchers.IO) {
            val ip = status.value.ip
                ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
            runCatching {
                val sigma = linkedMapOf<String, String>()
                // SigmaStar-only: no AStar PPG calls or 8192-port probing.
                // The OEM CGI script has broken getters for many properties.
                // Only keep values that pass parseConfigValue.
                DvrBackupCatalog.sigmaReadableProperties.forEach { property ->
                    val getter = when (property) {
                        "VideoRes" -> "Videores"
                        "LoopingVideo" -> "VideoClipTime"
                        else -> property
                    }
                    val raw = runCatching { get(DvrProtocol.configGet(ip, getter)) }.getOrNull()
                    raw?.let { parseConfigValue(it, getter) }?.takeIf { it.isNotBlank() }?.let {
                        sigma[property] = it
                    }
                }
                current.copy(
                    loading = false, error = null,
                    sigmaValues = sigma,
                    sigmaParkingMonitor = sigma["ParkingMonitor"]?.let {
                        it.equals("ENABLE", true) || it.equals("ON", true) || it == "1"
                    },
                    sigmaGSensor = sigma["GSensor"],
                    sigmaPowerOnGSensor = null,
                    aiActive = null, aiEnabled = null, parkingMode = null,
                    ppgAvailable = false, supportedPpgCommands = emptySet(),
                    roiReadable = false, sentinelReadable = false,
                    diagnosticResponses = emptyMap(),
                    lastRawResponse = "SigmaStar S38：读取到 ${sigma.size} 项可解析的设置；其他字段未验证，未执行 AStar 探测"
                )
            }
        }

    suspend fun collectSettingsBackup(
        appVersion: String,
        advanced: AdvancedDvrState,
    ): Result<DvrSettingsBackup> = withContext(Dispatchers.IO) {
        runCatching {
            val device = status.value
            check(device.isConnected) { "尚未发现记录仪" }

            val sigma = linkedMapOf<String, String>()
            DvrBackupCatalog.sigmaReadableProperties.forEach { property ->
                getSigmaProperty(property).getOrNull()?.let { value ->
                    if (value.isNotBlank()) sigma[property] = value
                }
            }

            DvrSettingsBackup(
                appVersion = appVersion,
                deviceModel = device.dvrModel,
                socVersion = device.socVersion,
                mcuVersion = device.mcuVersion,
                dvrEnabled = device.dvrEnabled,
                micEnabled = device.mic,
                sigma = sigma,
                aiEnabled = null,
                parkingMode = null,
                parkingGSensor = null,
                peopleRoi = null,
                peopleDetectDuration = null,
            )

        }
    }

    suspend fun writeSettingsBackup(uriString: String, backup: DvrSettingsBackup): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                resolver.openOutputStream(uri, "wt").use { output ->
                    checkNotNull(output) { "无法打开备份文件" }
                    output.writer(Charsets.UTF_8).use { writer ->
                        writer.write(backup.toJson())
                    }
                }
            }
        }

    suspend fun readSettingsBackup(uriString: String): Result<DvrSettingsBackup> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("无法读取备份文件")
                DvrSettingsBackup.fromJson(text)
            }
        }

    suspend fun restoreSettingsBackup(backup: DvrSettingsBackup): Result<RestoreReport> =
        withContext(Dispatchers.IO) {
            val ip = status.value.ip
                ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))

            runCatching {
                var applied = 0
                var skipped = 0
                var failed = 0
                var restartMayBeRequired = false
                val details = mutableListOf<String>()

                suspend fun applySetting(label: String, block: suspend () -> Result<String>) {
                    val result = block()
                    if (result.isSuccess) {
                        applied++
                        details += "成功：$label"
                    } else {
                        failed++
                        details += "失败：$label - ${result.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                }

                backup.dvrEnabled?.let { value ->
                    applySetting("DVR 开关=$value") { setDvr(value) }
                } ?: run { skipped++ }

                backup.micEnabled?.let { value ->
                    applySetting("录音开关=$value") { setMic(value) }
                } ?: run { skipped++ }

                backup.sigma.forEach { (property, value) ->
                    if (property !in DvrBackupCatalog.sigmaWritableProperties) {
                        skipped++
                        details += "跳过：$property 不在允许恢复列表"
                    } else {
                        when (property) {
                            "ParkingMonitor" -> applySetting("$property=$value") {
                                setSigmaParkingMonitor(value.equals("ENABLE", true) || value == "1" || value.equals("ON", true))
                            }
                            "GSensor" -> applySetting("$property=$value") { setSigmaGSensor(value) }
                            "PowerOnGSensor" -> applySetting("$property=$value") { setSigmaPowerOnGSensor(value) }
                            else -> applySetting("$property=$value") { setSigmaProperty(property, value) }
                        }
                    }
                }

                // AStar fields in legacy backup files are intentionally ignored.
                // Never dispatch PPG/Sentinel/ROI commands on a SigmaStar S38 device.
                if (backup.peopleRoi != null || backup.peopleDetectDuration != null ||
                    backup.parkingGSensor != null || backup.parkingMode != null ||
                    backup.aiEnabled != null) {
                    skipped++
                    details += "跳过：旧版备份中的 AStar/PPG 字段（SigmaStar 不支持）"
                }

                RestoreReport(
                    applied = applied,
                    skipped = skipped,
                    failed = failed,
                    restartMayBeRequired = restartMayBeRequired,
                    details = details,
                )
            }
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

    private fun probeRaw(url: String): String {
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                "HTTP " + resp.code + "\n" + body.take(450)
            }
        }.getOrElse { err ->
            "ERROR " + err.javaClass.simpleName + ": " + err.message
        }
    }

    private fun isPpgReachable(ip: String): Boolean {
        val raw = runCatching { get(DvrProtocol.aiActiveTest(ip)) }.getOrNull()
        return DvrResponseParser.isPpgResponse(raw, 9023)
    }

    private fun getSupportedPpgCommands(ip: String): Set<Int> {
        val raw = runCatching { get(DvrProtocol.ppgSupportedCommands(ip)) }.getOrNull() ?: return emptySet()
        return if (DvrResponseParser.isPpgResponse(raw)) DvrResponseParser.commandNumbers(raw) else emptySet()
    }

    private fun readPpgInt(ip: String, cmd: Int): Int? {
        val all = runCatching { get(DvrProtocol.ppgStatusAll(ip)) }.getOrNull()
        val fromAll = all?.takeIf { DvrResponseParser.isPpgResponse(it) }?.let(DvrResponseParser::ppgCommandMap)?.get(cmd)?.let { (statusValue, value) ->
            value?.toIntOrNull() ?: statusValue
        }
        if (fromAll != null) return fromAll

        val direct = runCatching { get(DvrProtocol.ppgRead(ip, cmd)) }.getOrNull() ?: return null
        return if (DvrResponseParser.isPpgResponse(direct, cmd)) DvrResponseParser.ppgInt(direct) else null
    }

    private suspend fun ppgCommand(
        cmd: Int,
        urlForIp: (String) -> String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val ip = status.value.ip
            ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))
        runCatching {
            check(isPpgReachable(ip)) { "PPG 命令处理器未确认，禁止写入 cmd=$cmd" }
            val raw = get(urlForIp(ip))
            ensureCommandExecuted(raw, cmd)
            raw
        }
    }

    private fun ensureCommandExecuted(raw: String, cmd: Int) {
        check(DvrResponseParser.isPpgResponse(raw, cmd)) {
            "cmd=$cmd 未返回匹配的 PPG/XML 结果，设置未验证；响应：" +
                raw.replace(Regex("\\s+"), " ").take(160)
        }
        val statusCode = DvrResponseParser.xmlStatus(raw)?.toIntOrNull()
        check(statusCode == null || statusCode == 0) {
            "cmd=$cmd 返回失败状态 $statusCode"
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

    private fun parseConfigValue(raw: String, property: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        // Some OEM GET shell branches accidentally echo the nvconf command
        // rather than executing it. Such text is NOT a live setting value.
        if (Regex("""(?i)(?:^|[=:\s])nvconf\s+get\s+\d+\s+""").containsMatchIn(text)) return null
        val lower = text.lowercase()
        if ("unsupported" in lower || "not support" in lower || "unknown property" in lower) return null
        if ("<html" in lower || "<!doctype html" in lower || "congratulations!" in lower) return null

        DvrResponseParser.xmlValue(text)?.takeIf { it.isNotBlank() }?.let { return it }

        val propertyRegex = Regex(
            """(?:^|[\s<>&;])${Regex.escape(property)}\s*[:=]\s*["']?([^"'<>;&\r\n]+)""",
            RegexOption.IGNORE_CASE
        )
        propertyRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }

        val genericValue = Regex(
            """\bvalue\s*[:=]\s*["']?([^"'<>;&\r\n]+)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!genericValue.isNullOrBlank()) return genericValue

        if (text.length <= 96 && '<' !in text && '\n' !in text && '\r' !in text &&
            !text.contains("error", true) && !text.contains("fail", true)
        ) {
            return text.trim('"', '\'', ' ')
        }
        return null
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
