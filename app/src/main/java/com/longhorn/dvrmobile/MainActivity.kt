package com.longhorn.dvrmobile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(this)
            } else {
                lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) { DvrApp() }
            }
        }
    }
}

@Composable
private fun DvrApp(vm: DvrViewModel = viewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
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
                        onClick = {
                            tab = index
                            if (index == 1 && media.files.isEmpty() && !media.loading) {
                                vm.loadMedia()
                            }
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { pad ->
        when (tab) {
            0 -> LiveScreen(status, message, vm, Modifier.padding(pad))
            1 -> MediaScreen(status, media, vm, Modifier.padding(pad))
            2 -> DeviceScreen(status, vm, Modifier.padding(pad))
            else -> SettingsScreen(status, Modifier.padding(pad))
        }
    }
}

@Composable
private fun LiveScreen(
    status: DvrStatus,
    message: String?,
    vm: DvrViewModel,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "行车记录仪",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (status.isConnected) "已发现 · ${status.ip}" else "正在通过车机热点发现设备…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(if (status.isConnected) "已连接" else "未连接") },
                leadingIcon = {
                    Icon(
                        if (status.isConnected) Icons.Default.CheckCircle else Icons.Default.Sync,
                        null
                    )
                }
            )
        }

        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            if (status.ip != null) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF111316))
                ) {
                    DvrRtspPlayer(DvrProtocol.rtsp(status.ip!!))
                    Surface(
                        modifier = Modifier.padding(12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                null,
                                tint = Color.Red,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                status.recordingLabel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFF111316)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("等待 DVR IP", color = Color.White)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("拍照", Icons.Default.PhotoCamera, Modifier.weight(1f)) { vm.capture() }
            QuickAction("事件录像", Icons.Default.EmergencyRecording, Modifier.weight(1f)) { vm.event() }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("开始录像", Icons.Default.FiberManualRecord, Modifier.weight(1f)) {
                vm.recordStart()
            }
            QuickAction("停止录像", Icons.Default.StopCircle, Modifier.weight(1f)) {
                vm.recordStop()
            }
        }

        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("设备状态", style = MaterialTheme.typography.titleLarge)
                StatusRow("录像", status.recordingLabel)
                StatusRow("SD 卡", status.sdLabel)
                StatusRow(
                    "SoC",
                    listOfNotNull(status.socVersion, status.innerVersion)
                        .joinToString(".")
                        .ifBlank { "—" }
                )
                StatusRow("MCU", status.mcuVersion ?: "—")
                StatusRow("型号", status.dvrModel ?: "—")
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MediaScreen(
    status: DvrStatus,
    state: MediaUiState,
    vm: DvrViewModel,
    modifier: Modifier,
) {
    var selectedFile by remember { mutableStateOf<DvrMediaFile?>(null) }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "记录文件",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.loadMedia(state.kind) }, enabled = status.isConnected) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val kinds = listOf(
                    DvrMediaFile.Kind.NORMAL to "普通录像",
                    DvrMediaFile.Kind.EVENT to "事件录像",
                    DvrMediaFile.Kind.PHOTO to "照片",
                )
                kinds.forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = state.kind == kind,
                        onClick = {
                            selectedFile = null
                            vm.loadMedia(kind)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }

        selectedFile?.let { file ->
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            file.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                        )
                        IconButton(onClick = { selectedFile = null }) {
                            Icon(Icons.Default.Close, "关闭预览")
                        }
                    }
                    val url = vm.mediaUrl(file)
                    if (file.kind != DvrMediaFile.Kind.PHOTO && url != null) {
                        HttpVideoPlayer(url)
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                url ?: "无法生成文件地址",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(state.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.loadMedia(state.kind) }) {
                            Text("重新加载")
                        }
                    }
                }
            }

            state.files.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("未解析到文件")
                        Text(
                            "如果记录仪实际有文件，可在设备页查看 UDP 状态；后续可根据真实 CGI 返回格式继续适配。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.files, key = { it.remotePath }) { file ->
                        ElevatedCard(
                            Modifier.fillMaxWidth().clickable { selectedFile = file },
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (file.kind == DvrMediaFile.Kind.PHOTO) {
                                                Icons.Default.Image
                                            } else {
                                                Icons.Default.Movie
                                            },
                                            null
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        file.remotePath,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(status: DvrStatus, vm: DvrViewModel, modifier: Modifier) {
    var ip by remember(status.ip) { mutableStateOf(status.ip.orEmpty()) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "设备",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("网络诊断", style = MaterialTheme.typography.titleLarge)
                Text(
                    "优先监听 UDP 49142 / 53296；如果车机热点不转发广播，可手动输入 DVR IP。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    ip,
                    { ip = it },
                    label = { Text("DVR IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.manualIp(ip) },
                    enabled = ip.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("连接并测试")
                }
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
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("手机版重构", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Android 16 · Material 3 · Edge-to-edge",
                    color = MaterialTheme.colorScheme.primary
                )
                StatusRow("DVR IP", status.ip ?: "未发现")
                StatusRow("RTSP", status.ip?.let { DvrProtocol.rtsp(it) } ?: "—")
                StatusRow("发现端口", "UDP 49142 / 53296")
                StatusRow("视频传输", "RTSP / RTP-over-TCP")
            }
        }
    }
}

@Composable
private fun QuickAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    action: () -> Unit,
) {
    FilledTonalButton(
        onClick = action,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
