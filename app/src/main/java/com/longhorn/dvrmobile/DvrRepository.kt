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

    suspend fun syncAdvancedSettings(current: AdvancedDvrState): Result<AdvancedDvrState> =
        withContext(Dispatchers.IO) {
            val ip = status.value.ip
                ?: return@withContext Result.failure(IllegalStateException("尚未发现记录仪"))

            runCatching {
                var next = current.copy(loading = true, error = null, ppgAvailable = false, aiActive = null, aiEnabled = null, parkingMode = null, parkingGSensorLevel = null, roiReadable = null, sentinelReadable = null, supportedPpgCommands = emptySet(), diagnosticResponses = emptyMap())
                val notes = mutableListOf<String>()
                val nativeDiagnostics = linkedMapOf<String, String>()
                // Read-only targeted probes: avoid 20 sequential timeout-prone calls.
                val endpoints = listOf(
                    Triple(80, "deviceattr", "getdeviceattr.cgi"),
                    Triple(80, "workstate", "getworkstate.cgi"),
                    Triple(80, "workmode", "getworkmodecmd.cgi"),
                    Triple(8192, "deviceattr", "getdeviceattr.cgi"),
                    Triple(8192, "workstate", "getworkstate.cgi"),
                    Triple(8192, "workmode", "getworkmodecmd.cgi")
                )
                endpoints.forEach { (port, label, name) ->
                    val root = DvrProtocol.nativeCgi(ip, port, name)
                    nativeDiagnostics["$port/$label/root"] = probeRaw(root)
                    // Check CGI directory only where an HTTP server is reachable.
                    if (port == 80) {
                        val cgi = DvrProtocol.nativeCgi(ip, port, "cgi-bin/$name")
                        nativeDiagnostics["$port/$label/cgi-bin"] = probeRaw(cgi)
                    }
                }

                // AStar / PPG: directly probe known getters first.
                // Some production builds accept vendor 90xx commands but do not expose
                // the generic 3002/3014 capability/status tables.
                val direct9023 = runCatching { get(DvrProtocol.aiActiveTest(ip)) }.getOrNull()
                val direct9098 = runCatching { get(DvrProtocol.getPeopleRoi(ip)) }.getOrNull()
                val direct9137 = runCatching { get(DvrProtocol.ppgRead(ip, 9137)) }.getOrNull()

                val supportedRaw = runCatching { get(DvrProtocol.ppgSupportedCommands(ip)) }.getOrNull()
                val supportedCommands = supportedRaw?.takeIf { DvrResponseParser.isPpgResponse(it) }?.let(DvrResponseParser::commandNumbers).orEmpty()

                val ppgRaw = runCatching { get(DvrProtocol.ppgStatusAll(ip)) }.getOrNull()
                val ppgMap = ppgRaw?.takeIf { DvrResponseParser.isPpgResponse(it) }?.let(DvrResponseParser::ppgCommandMap).orEmpty()

                val valid9023 = DvrResponseParser.isPpgResponse(direct9023, 9023)
                val valid9098 = DvrResponseParser.isPpgResponse(direct9098, 9098)
                val valid9137 = DvrResponseParser.isPpgResponse(direct9137, 9137)
                val directAiActive = direct9023?.takeIf { valid9023 }?.let(DvrResponseParser::ppgInt)
                val directRoi = direct9098?.takeIf { valid9098 }?.let(DvrResponseParser::normalizeRoi)
                val directParkingMode = direct9137?.takeIf { valid9137 }?.let(DvrResponseParser::ppgInt)
                val ppgAvailable = valid9023 || valid9098 || valid9137 ||
                    ppgMap.isNotEmpty() || supportedCommands.isNotEmpty()

                val astarDetected =
                    ppgAvailable ||
                    supportedCommands.any { it in 9000..9999 } ||
                    ppgMap.keys.any { it in setOf(9096, 9099, 9106, 9137) }

                if (astarDetected) {
                    val aiEnabled = ppgMap[9096]?.let { (statusValue, value) ->
                        // Firmware Status ALL stores ALG_ENABLE in Status on this product.
                        statusValue?.let { it == 1 } ?: value?.toIntOrNull()?.let { it == 1 }
                    }

                    fun commandInt(cmd: Int): Int? {
                        val item = ppgMap[cmd] ?: return null
                        return item.second?.toIntOrNull() ?: item.first
                    }

                    val parkingModeValue = commandInt(9137) ?: directParkingMode ?: readPpgInt(ip, 9137)
                    val parkingMode = ParkingMode.fromValue(parkingModeValue)
                    val parkingGSensor = commandInt(9106)?.takeIf { it in 0..3 }
                    val duration = commandInt(9099)?.takeIf { it in 0..120 }
                    val aiActive = directAiActive?.let { it == 1 } ?: aiActiveTest().getOrNull()
                    val roi = directRoi ?: getPeopleRoi().getOrNull()

                    next = next.copy(
                        aiActive = aiActive ?: next.aiActive,
                        aiEnabled = aiEnabled ?: next.aiEnabled,
                        parkingMode = parkingMode ?: next.parkingMode,
                        parkingGSensorLevel = parkingGSensor ?: next.parkingGSensorLevel,
                        peopleDetectDuration = duration ?: next.peopleDetectDuration,
                        peopleRoi = roi ?: next.peopleRoi,
                        ppgAvailable = ppgAvailable,
                        supportedPpgCommands = supportedCommands,
                        roiReadable = if (supportedCommands.isEmpty()) roi != null else (9098 in supportedCommands && roi != null),
                        sentinelReadable = parkingModeValue != null,
                        diagnosticResponses = buildMap {
                            direct9023?.let { put("9023", it.take(500)) }
                            direct9098?.let { put("9098", it.take(500)) }
                            direct9137?.let { put("9137", it.take(500)) }
                            supportedRaw?.let { put("3002", it.take(500)) }
                            ppgRaw?.let { put("3014", it.take(500)) }
                        },
                    )
                    notes += buildString {
                        append("AStar/PPG 已同步")
                        if (supportedCommands.isNotEmpty()) {
                            append("；扩展CMD=")
                            append(supportedCommands.sorted().filter { it >= 9000 }.joinToString(","))
                        }
                        if (roi == null) append("；ROI不可读")
                        if (parkingModeValue == null) append("；Sentinel状态不可读")
                        if (supportedCommands.isEmpty()) append("；3002未提供能力列表，已改用直接探测")
                    }
                }

                // SigmaStar: only enumerate the full property catalog after a successful probe,
                // avoiding dozens of pointless requests on AStar devices.
                val sigmaProbe = getSigmaProperty("VideoRes").getOrNull()
                if (sigmaProbe != null) {
                    val sigma = linkedMapOf<String, String>()
                    sigma["VideoRes"] = sigmaProbe
                    DvrBackupCatalog.sigmaReadableProperties
                        .filterNot { it == "VideoRes" }
                        .forEach { property ->
                            getSigmaProperty(property).getOrNull()?.let { value ->
                                if (value.isNotBlank()) sigma[property] = value
                            }
                        }

                    next = next.copy(
                        sigmaValues = sigma,
                        sigmaParkingMonitor = sigma["ParkingMonitor"]?.let {
                            it.equals("ENABLE", true) || it.equals("ON", true) || it == "1"
                        },
                        sigmaGSensor = sigma["GSensor"],
                        sigmaPowerOnGSensor = sigma["PowerOnGSensor"],
                    )
                    notes += "SigmaStar 设置已同步 ${sigma.size} 项"
                }

                if (!astarDetected && sigmaProbe == null) {
                    val nativeHit = nativeDiagnostics.any { entry ->
                        val value = entry.value
                        !value.startsWith("HTTP 404") &&
                        !value.startsWith("ERROR") &&
                        !value.contains("Congratulations! The server is up", ignoreCase = true)
                    }
                    notes += if (nativeHit) {
                        "PPG 未挂载；已发现原生 CGI 响应，请查看诊断"
                    } else {
                        "PPG 未挂载；80/8192 原生 CGI 暂未识别"
                    }
                }

                next.copy(
                    loading = false,
                    ppgAvailable = if (next.ppgAvailable == true) true else false,
                    diagnosticResponses = next.diagnosticResponses + nativeDiagnostics.mapKeys { "native:" + it.key },
                    lastRawResponse = notes.joinToString("；"),
                    error = null,
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

            val liveRoi = getPeopleRoi().getOrNull() ?: advanced.peopleRoi.takeIf {
                DvrResponseParser.isValidRoi(it)
            }
            val aiActive = aiActiveTest().getOrNull()

            DvrSettingsBackup(
                appVersion = appVersion,
                deviceModel = device.dvrModel,
                socVersion = device.socVersion,
                mcuVersion = device.mcuVersion,
                dvrEnabled = device.dvrEnabled,
                micEnabled = device.mic,
                sigma = sigma,
                aiEnabled = advanced.aiEnabled,
                parkingMode = advanced.parkingMode?.value,
                parkingGSensor = advanced.parkingGSensorLevel,
                peopleRoi = liveRoi,
                peopleDetectDuration = advanced.peopleDetectDuration,
            ).also {
                // AI active is deliberately not persisted as a setting; it is runtime/license state.
                @Suppress("UNUSED_VARIABLE")
                val ignoredRuntimeState = aiActive
            }
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

                backup.peopleRoi?.let { roi ->
                    if (DvrResponseParser.isValidRoi(roi)) {
                        applySetting("人员检测 ROI") { setPeopleRoi(roi) }
                    } else {
                        skipped++
                        details += "跳过：备份中的 ROI 无效"
                    }
                } ?: run { skipped++ }

                backup.peopleDetectDuration?.let { value ->
                    if (value in 0..120) {
                        applySetting("人员检测持续时间=$value") { setPeopleDetectDuration(value) }
                    } else {
                        skipped++
                        details += "跳过：人员检测持续时间超出安全范围"
                    }
                } ?: run { skipped++ }

                backup.parkingGSensor?.let { level ->
                    if (level in 0..3) {
                        applySetting("AStar 停车 G-sensor=$level") { setParkingGSensor(level) }
                    } else {
                        skipped++
                        details += "跳过：AStar 停车 G-sensor 档位无效"
                    }
                } ?: run { skipped++ }

                backup.parkingMode?.let { value ->
                    val mode = ParkingMode.fromValue(value)
                    if (mode != null) {
                        applySetting("AStar 驻车模式=${mode.label}") { setParkingMode(mode) }
                    } else {
                        skipped++
                        details += "跳过：未知驻车模式 $value"
                    }
                } ?: run { skipped++ }

                // Apply AI enable last because original firmware may immediately reboot after changing it.
                backup.aiEnabled?.let { enabled ->
                    restartMayBeRequired = true
                    applySetting("AI 总开关=$enabled") { setAlgEnabled(enabled) }
                } ?: run { skipped++ }

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
