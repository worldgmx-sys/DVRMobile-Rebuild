package com.longhorn.dvrmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) { DvrApp() }
            }
        }
    }
}

@Composable
private fun DvrApp(vm: DvrViewModel = viewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Icons.Default.Videocam, "实时", 0),
                    Triple(Icons.Default.VideoLibrary, "录像", 1),
                    Triple(Icons.Default.Devices, "设备", 2),
                    Triple(Icons.Default.Settings, "设置", 3),
                ).forEach { (icon, label, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { pad ->
        when (tab) {
            0 -> LiveScreen(status, message, vm, Modifier.padding(pad))
            1 -> Placeholder("录像", "普通录像、事件录像和照片将在第二阶段接入真实列表接口。", Modifier.padding(pad))
            2 -> DeviceScreen(status, vm, Modifier.padding(pad))
            else -> SettingsScreen(status, Modifier.padding(pad))
        }
    }
}

@Composable
private fun LiveScreen(status: DvrStatus, message: String?, vm: DvrViewModel, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("行车记录仪", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(if (status.isConnected) "已发现 · ${status.ip}" else "正在通过车机热点发现设备…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(onClick = {}, label = { Text(if (status.isConnected) "已连接" else "未连接") }, leadingIcon = {
                Icon(if (status.isConnected) Icons.Default.CheckCircle else Icons.Default.Sync, null)
            })
        }

        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color(0xFF111316)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (status.ip != null) DvrProtocol.rtsp(status.ip!!) else "等待 DVR IP", color = Color.White)
                    Text("RTSP 播放器将在下一构建阶段接入", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("拍照", Icons.Default.PhotoCamera, Modifier.weight(1f)) { vm.capture() }
            QuickAction("事件录像", Icons.Default.EmergencyRecording, Modifier.weight(1f)) { vm.event() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("开始录像", Icons.Default.FiberManualRecord, Modifier.weight(1f)) { vm.recordStart() }
            QuickAction("停止录像", Icons.Default.StopCircle, Modifier.weight(1f)) { vm.recordStop() }
        }

        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("设备状态", style = MaterialTheme.typography.titleLarge)
                StatusRow("录像", status.recordingLabel)
                StatusRow("SD 卡", status.sdLabel)
                StatusRow("SoC", listOfNotNull(status.socVersion, status.innerVersion).joinToString(".").ifBlank { "—" })
                StatusRow("MCU", status.mcuVersion ?: "—")
                StatusRow("型号", status.dvrModel ?: "—")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DeviceScreen(status: DvrStatus, vm: DvrViewModel, modifier: Modifier) {
    var ip by remember(status.ip) { mutableStateOf(status.ip.orEmpty()) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("设备", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("网络诊断", style = MaterialTheme.typography.titleLarge)
                Text("优先监听 UDP 49142 / 53296；如果车机热点不转发广播，可手动输入 DVR IP。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(ip, { ip = it }, label = { Text("DVR IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.manualIp(ip) }, enabled = ip.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("连接并测试") }
            }
        }
        status.lastMessage?.let {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("最近 UDP 广播", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(status: DvrStatus, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("手机版重构", style = MaterialTheme.typography.titleLarge)
                Text("Android 16 · Material 3 · Edge-to-edge", color = MaterialTheme.colorScheme.primary)
                StatusRow("DVR IP", status.ip ?: "未发现")
                StatusRow("RTSP", status.ip?.let { DvrProtocol.rtsp(it) } ?: "—")
                StatusRow("发现端口", "UDP 49142 / 53296")
            }
        }
    }
}

@Composable private fun QuickAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, action: () -> Unit) {
    FilledTonalButton(onClick = action, modifier = modifier.height(58.dp), shape = RoundedCornerShape(18.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun Placeholder(title: String, body: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Construction, null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
