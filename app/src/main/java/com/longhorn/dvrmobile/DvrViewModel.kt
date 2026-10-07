package com.longhorn.dvrmobile

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

class DvrViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DvrRepository(app)

    val status = repo.status

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _media = MutableStateFlow(MediaUiState())
    val media: StateFlow<MediaUiState> = _media.asStateFlow()

    private val _downloadDirectory = MutableStateFlow(repo.downloadTreeUri())
    val downloadDirectory: StateFlow<String?> = _downloadDirectory.asStateFlow()

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

    fun probe() = action("正在测试连接") { repo.probe() }
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
        viewModelScope.launch {
            _message.value = "正在下载：${file.name}"
            val r = repo.download(file)
            _message.value = if (r.isSuccess) {
                "下载完成：${file.name}"
            } else {
                "下载失败：${r.exceptionOrNull()?.message}"
            }
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
