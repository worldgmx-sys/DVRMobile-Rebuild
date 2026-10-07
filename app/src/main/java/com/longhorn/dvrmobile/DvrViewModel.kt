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

    init {
        repo.startDiscovery()
    }

    fun manualIp(ip: String) {
        repo.manualIp(ip)
        probe()
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
    fun mic(on: Boolean) = action(if (on) "开启麦克风" else "关闭麦克风") { repo.setMic(on) }

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
