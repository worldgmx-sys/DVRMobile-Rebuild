package com.longhorn.dvrmobile

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class DvrDiscovery(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val _status = MutableStateFlow(DvrStatus())
    val status: StateFlow<DvrStatus> = _status.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("dvr-mobile-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        job = scope.launch {
            val ports = listOf(49142, 53296)
            val sockets = ports.mapNotNull { port ->
                runCatching {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        soTimeout = 900
                        bind(InetSocketAddress(port))
                    }
                }.getOrNull()
            }
            val buf = ByteArray(2048)
            try {
                while (isActive) {
                    for (socket in sockets) {
                        try {
                            val packet = DatagramPacket(buf, buf.size)
                            socket.receive(packet)
                            val text = packet.data.decodeToString(0, packet.length).trim()
                            if (text.contains("IP=")) _status.value = parse(text, _status.value)
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Throwable) {
                        }
                    }
                }
            } finally {
                sockets.forEach { it.close() }
            }
        }
    }

    fun setManualIp(ip: String) {
        if (ip.isNotBlank()) _status.value = _status.value.copy(ip = ip.trim())
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun parse(message: String, old: DvrStatus): DvrStatus {
        val map = message.lineSequence()
            .map { it.trim() }
            .filter { '=' in it }
            .associate {
                val p = it.indexOf('=')
                it.substring(0, p) to it.substring(p + 1)
            }
        fun flag(k: String): Boolean? = map[k]?.let { it == "1" || it.equals("ON", true) }
        return old.copy(
            ip = map["IP"] ?: old.ip,
            socVersion = map["SOCVer"] ?: old.socVersion,
            innerVersion = map["INVer"] ?: old.innerVersion,
            mcuVersion = map["MCUVer"] ?: old.mcuVersion,
            record = map["Record"] ?: old.record,
            sd = map["Sd"] ?: old.sd,
            totalSize = map["TotalSize"] ?: old.totalSize,
            usedNormalSpace = map["usedNormalSpace"] ?: old.usedNormalSpace,
            usedEventSpace = map["usedEventSpace"] ?: old.usedEventSpace,
            usedPhotoSpace = map["usedPhotoSpace"] ?: old.usedPhotoSpace,
            eventStatus = map["eventStatus"] ?: old.eventStatus,
            mic = flag("mic") ?: old.mic,
            dvrEnabled = flag("dvr") ?: old.dvrEnabled,
            rtspFlag = map["rtsp"] ?: old.rtspFlag,
            dvrModel = map["dvrmodel"] ?: old.dvrModel,
            lastMessage = message,
        )
    }
}
