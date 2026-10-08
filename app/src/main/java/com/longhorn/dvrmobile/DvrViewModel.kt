package com.longhorn.dvr.worldgm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaUiState(
    val kind: DvrMediaFile.Kind = DvrMediaFile.Kind.NORMAL,
    val loading: Boolean = false,
    val files: List<DvrMediaFile> = emptyList(),
    val error: String? = null,
)

enum class DownloadState { QUEUED, DOWNLOADING, COMPLETED, FAILED }

data class DownloadTask(
    val id: String,
    val fileName: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
}

class DvrViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DvrRepository(app)

    val status = repo.status

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _media = MutableStateFlow(MediaUiState())
    val media: StateFlow<MediaUiState> = _media.asStateFlow()

    private val _downloadDirectory = MutableStateFlow(repo.downloadTreeUri())
    val downloadDirectory: StateFlow<String?> = _downloadDirectory.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    private val _advanced = MutableStateFlow(AdvancedDvrState())
    val advanced: StateFlow<AdvancedDvrState> = _advanced.asStateFlow()

    init {
        repo.startDiscovery()
    }

    fun manualIp(ip: String) {
        repo.manualIp(ip)
        probe()
    }

    fun setDownloadDirectory(uri: String?) {
        repo.setDownloadTreeUri(uri)
        _downloadDirectory.value = uri
        _message.value = if (uri == null) "已清除下载目录" else "已设置下载目录"
    }

    fun probe() {
        viewModelScope.launch {
            val probe = repo.probe()
            if (probe.isFailure) {
                _message.value = "连接测试：${probe.exceptionOrNull()?.message}"
                return@launch
            }
            repo.initializeClient(BuildConfig.VERSION_CODE.toLong())
            _message.value = "连接测试：成功"
            refreshAdvanced()
        }
    }
    fun recordStart() = action("开始录像") { repo.startRecording() }
    fun recordStop() = action("停止录像") { repo.stopRecording() }

    fun capture() {
        viewModelScope.launch {
            _message.value = "正在拍照…"
            val r = repo.captureVerified()
            if (r.isFailure) {
                _message.value = "拍照失败：${r.exceptionOrNull()?.message}"
                return@launch
            }
            val newPhoto = r.getOrNull()
            if (newPhoto != null) {
                _message.value = "拍照成功：${newPhoto.name}"
                loadMedia(DvrMediaFile.Kind.PHOTO)
            } else {
                _message.value = "拍照命令已发送，但未检测到新增照片"
            }
        }
    }

    fun event() = action("事件录像") { repo.eventRecording() }
    fun mic(on: Boolean) = action(if (on) "开启录音" else "关闭录音") { repo.setMic(on) }
    fun removeSd() = action("安全移除内存卡") { repo.removeSd() }
    fun formatSd() = action("格式化内存卡") { repo.formatSd() }

    fun authorizeApp(value: String) = action("APP 授权") { repo.authorizeApp(value) }
    fun setAuthTime(value: String) = action("设置录音授权时效") { repo.setAuthTime(value) }

    fun setAudioAuthPreset(days: Int) {
        val value = when (days) {
            360 -> "0"
            180 -> "1"
            90 -> "2"
            else -> return
        }
        action("设置录音授权 ${days} 天") { repo.setAuthTime(value) }
    }

    fun refreshAdvanced() {
        viewModelScope.launch {
            _advanced.value = _advanced.value.copy(loading = true, error = null)
            val ai = repo.aiActiveTest()
            val roi = repo.getPeopleRoi()
            _advanced.value = _advanced.value.copy(
                loading = false,
                aiActive = ai.getOrNull(),
                peopleRoi = roi.getOrNull() ?: _advanced.value.peopleRoi,
                lastRawResponse = when {
                    ai.isFailure -> ai.exceptionOrNull()?.message
                    roi.isFailure -> roi.exceptionOrNull()?.message
                    else -> "高级能力探测完成"
                },
                error = null,
            )
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _message.value = if (enabled) "正在开启 AI，设备可能自动重启…" else "正在关闭 AI，设备可能自动重启…"
            val r = repo.setAlgEnabled(enabled)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(aiEnabled = enabled)
                _message.value = "AI 功能设置成功；原厂固件可能立即重启"
            } else {
                _message.value = "AI 功能设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setParkingMode(mode: ParkingMode) {
        viewModelScope.launch {
            val r = repo.setParkingMode(mode)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(parkingMode = mode)
                _message.value = "驻车模式已设置为：${mode.label}"
            } else {
                _message.value = "驻车模式设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setParkingGSensor(level: Int) {
        viewModelScope.launch {
            val r = repo.setParkingGSensor(level)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(parkingGSensorLevel = level)
                _message.value = "停车 G-sensor 已设置为档位 $level"
            } else {
                _message.value = "停车 G-sensor 设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun loadPeopleRoi() {
        viewModelScope.launch {
            val r = repo.getPeopleRoi()
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(peopleRoi = r.getOrThrow())
                _message.value = "已读取人员检测区域"
            } else {
                _message.value = "读取 ROI 失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setPeopleRoi(roi: String) {
        viewModelScope.launch {
            val r = repo.setPeopleRoi(roi)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(peopleRoi = roi)
                _message.value = "人员检测区域已更新"
            } else {
                _message.value = "ROI 设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setPeopleDetectDuration(value: Int) {
        viewModelScope.launch {
            val r = repo.setPeopleDetectDuration(value)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(peopleDetectDuration = value)
                _message.value = "人员检测持续时间参数已设置"
            } else {
                _message.value = "人员检测持续时间设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setSigmaParkingMonitor(enabled: Boolean) {
        viewModelScope.launch {
            val r = repo.setSigmaParkingMonitor(enabled)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(sigmaParkingMonitor = enabled)
                _message.value = "SigmaStar 停车监控已${if (enabled) "开启" else "关闭"}"
            } else {
                _message.value = "停车监控设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setSigmaGSensor(value: String) {
        viewModelScope.launch {
            val r = repo.setSigmaGSensor(value)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(sigmaGSensor = value)
                _message.value = "行车 G-sensor 已设置为 $value"
            } else {
                _message.value = "行车 G-sensor 设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setSigmaPowerOnGSensor(value: String) {
        viewModelScope.launch {
            val r = repo.setSigmaPowerOnGSensor(value)
            if (r.isSuccess) {
                _advanced.value = _advanced.value.copy(sigmaPowerOnGSensor = value)
                _message.value = "停车唤醒 G-sensor 已设置为 $value"
            } else {
                _message.value = "停车唤醒 G-sensor 设置失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun setSigmaProperty(property: String, value: String, label: String = property) {
        viewModelScope.launch {
            val result = repo.setSigmaProperty(property, value)
            _message.value = if (result.isSuccess) {
                "$label 已设置为 $value"
            } else {
                "$label 设置失败：${result.exceptionOrNull()?.message}"
            }
        }
    }


    fun exportSettingsBackup(uriString: String) {
        viewModelScope.launch {
            _message.value = "正在读取记录仪设置并生成备份…"
            val backupResult = repo.collectSettingsBackup(
                appVersion = BuildConfig.VERSION_NAME,
                advanced = _advanced.value,
            )
            if (backupResult.isFailure) {
                _message.value = "备份失败：${backupResult.exceptionOrNull()?.message}"
                return@launch
            }

            val backup = backupResult.getOrThrow()
            val writeResult = repo.writeSettingsBackup(uriString, backup)
            _message.value = if (writeResult.isSuccess) {
                "设置备份完成：已采集 ${backup.sigma.size} 项 SigmaStar 参数" +
                    if (backup.peopleRoi != null) "，包含 Sentinel ROI" else ""
            } else {
                "备份文件写入失败：${writeResult.exceptionOrNull()?.message}"
            }
        }
    }

    fun restoreSettingsBackup(uriString: String) {
        viewModelScope.launch {
            _message.value = "正在读取备份文件…"
            val backupResult = repo.readSettingsBackup(uriString)
            if (backupResult.isFailure) {
                _message.value = "读取备份失败：${backupResult.exceptionOrNull()?.message}"
                return@launch
            }

            val backup = backupResult.getOrThrow()
            val current = status.value
            val modelMismatch = !backup.deviceModel.isNullOrBlank() &&
                !current.dvrModel.isNullOrBlank() &&
                !backup.deviceModel.equals(current.dvrModel, ignoreCase = true)

            if (modelMismatch) {
                _message.value = "恢复已停止：备份设备型号 ${backup.deviceModel} 与当前设备 ${current.dvrModel} 不一致"
                return@launch
            }

            _message.value = "正在恢复记录仪设置，请保持设备供电和网络连接…"
            val restore = repo.restoreSettingsBackup(backup)
            if (restore.isSuccess) {
                val report = restore.getOrThrow()
                _message.value = report.summary
                _advanced.value = _advanced.value.copy(
                    aiEnabled = backup.aiEnabled ?: _advanced.value.aiEnabled,
                    parkingMode = ParkingMode.fromValue(backup.parkingMode) ?: _advanced.value.parkingMode,
                    parkingGSensorLevel = backup.parkingGSensor ?: _advanced.value.parkingGSensorLevel,
                    peopleRoi = backup.peopleRoi ?: _advanced.value.peopleRoi,
                    peopleDetectDuration = backup.peopleDetectDuration ?: _advanced.value.peopleDetectDuration,
                    sigmaParkingMonitor = backup.sigma["ParkingMonitor"]?.let {
                        it.equals("ENABLE", true) || it.equals("ON", true) || it == "1"
                    } ?: _advanced.value.sigmaParkingMonitor,
                    sigmaGSensor = backup.sigma["GSensor"] ?: _advanced.value.sigmaGSensor,
                    sigmaPowerOnGSensor = backup.sigma["PowerOnGSensor"] ?: _advanced.value.sigmaPowerOnGSensor,
                )
            } else {
                _message.value = "恢复失败：${restore.exceptionOrNull()?.message}"
            }
        }
    }

    fun delete(file: DvrMediaFile) {
        viewModelScope.launch {
            val r = repo.deleteMedia(file)
            _message.value = if (r.isSuccess) "已删除：${file.name}" else "删除失败：${r.exceptionOrNull()?.message}"
            if (r.isSuccess) loadMedia(_media.value.kind)
        }
    }

    fun move(file: DvrMediaFile) {
        viewModelScope.launch {
            val r = repo.moveMedia(file)
            _message.value = if (r.isSuccess) {
                if (file.kind == DvrMediaFile.Kind.NORMAL) "已锁定为事件录像" else "已解除事件锁定"
            } else {
                "文件迁移失败：${r.exceptionOrNull()?.message}"
            }
            if (r.isSuccess) loadMedia(_media.value.kind)
        }
    }

    fun download(file: DvrMediaFile) {
        val taskId = file.remotePath + "#" + System.currentTimeMillis()
        _downloads.value = listOf(
            DownloadTask(id = taskId, fileName = file.name, state = DownloadState.QUEUED)
        ) + _downloads.value

        viewModelScope.launch {
            updateDownload(taskId) { it.copy(state = DownloadState.DOWNLOADING) }
            val r = repo.download(file) { downloaded, total ->
                updateDownload(taskId) {
                    it.copy(
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        state = DownloadState.DOWNLOADING
                    )
                }
            }
            if (r.isSuccess) {
                updateDownload(taskId) {
                    it.copy(
                        downloadedBytes = if (it.totalBytes > 0) it.totalBytes else it.downloadedBytes,
                        state = DownloadState.COMPLETED
                    )
                }
                _message.value = "下载完成：${file.name}"
            } else {
                updateDownload(taskId) {
                    it.copy(
                        state = DownloadState.FAILED,
                        error = r.exceptionOrNull()?.message ?: "未知错误"
                    )
                }
                _message.value = "下载失败：${r.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearFinishedDownloads() {
        _downloads.value = _downloads.value.filter {
            it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING
        }
    }

    private fun updateDownload(id: String, transform: (DownloadTask) -> DownloadTask) {
        _downloads.value = _downloads.value.map { task ->
            if (task.id == id) transform(task) else task
        }
    }

    fun uploadFirmware(uri: String, kind: FirmwareKind) {
        viewModelScope.launch {
            _message.value = "正在上传${if (kind == FirmwareKind.SOC) "SOC" else "MCU"}固件…"
            val r = repo.uploadFirmware(uri, kind)
            _message.value = if (r.isSuccess) "固件已上传，等待设备升级" else "固件上传失败：${r.exceptionOrNull()?.message}"
        }
    }

    fun loadMedia(kind: DvrMediaFile.Kind = _media.value.kind) {
        _media.value = _media.value.copy(kind = kind, loading = true, error = null)
        viewModelScope.launch {
            val r = repo.listMedia(kind)
            _media.value = if (r.isSuccess) {
                MediaUiState(kind = kind, files = r.getOrDefault(emptyList()))
            } else {
                MediaUiState(
                    kind = kind,
                    error = r.exceptionOrNull()?.message ?: "读取列表失败"
                )
            }
        }
    }

    fun mediaUrl(file: DvrMediaFile): String? = repo.mediaUrl(file)
    fun thumbnailUrl(file: DvrMediaFile): String? = repo.thumbnailUrl(file)

    private fun action(label: String, block: suspend () -> Result<String>) {
        viewModelScope.launch {
            val r = block()
            _message.value = if (r.isSuccess) {
                "$label：成功"
            } else {
                "$label：${r.exceptionOrNull()?.message}"
            }
        }
    }

    override fun onCleared() {
        repo.stopDiscovery()
        super.onCleared()
    }
}
