package com.longhorn.dvrmobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DvrViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DvrRepository(app)
    val status = repo.status
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { repo.startDiscovery() }

    fun manualIp(ip: String) { repo.manualIp(ip); probe() }
    fun probe() = run { action("正在测试连接") { repo.probe() } }
    fun recordStart() = action("开始录像") { repo.startRecording() }
    fun recordStop() = action("停止录像") { repo.stopRecording() }
    fun capture() = action("拍照") { repo.capture() }
    fun event() = action("事件录像") { repo.eventRecording() }
    fun mic(on: Boolean) = action(if (on) "开启麦克风" else "关闭麦克风") { repo.setMic(on) }

    private fun action(label: String, block: suspend () -> Result<String>) {
        viewModelScope.launch {
            val r = block()
            _message.value = if (r.isSuccess) "$label：成功" else "$label：${r.exceptionOrNull()?.message}"
        }
    }

    override fun onCleared() {
        repo.stopDiscovery()
        super.onCleared()
    }
}
