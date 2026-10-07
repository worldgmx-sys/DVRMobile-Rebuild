package com.longhorn.dvrmobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

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
    val downloadDirectory by vm.downloadDirectory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var pendingFirmwareKind by remember { mutableStateOf<FirmwareKind?>(null) }

    val directoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setDownloadDirectory(uri.toString())
        }
    }

    val firmwareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val kind = pendingFirmwareKind
        if (uri != null && kind != null) vm.uploadFirmware(uri.toString(), kind)
        pendingFirmwareKind = null
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Icons.Default.Videocam, "实时", 0),
                    Triple(Icons.Default.VideoLibrary, "文件", 1),
                    Triple(Icons.Default.Devices, "设备", 2),
                    Triple(Icons.Default.Settings, "设置", 3),
                ).forEach { (icon, label, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            if (index == 1 && media.files.isEmpty() && !media.loading) vm.loadMedia()
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
            1 -> MediaScreen(status, media, message, vm, Modifier.padding(pad))
            2 -> DeviceScreen(status, vm, Modifier.padding(pad))
            else -> SettingsScreen(
                status = status,
                message = message,
                downloadDirectory = downloadDirectory,
                vm = vm,
                onChooseDownloadDirectory = { directoryLauncher.launch(null) },
                onChooseFirmware = { kind ->
                    pendingFirmwareKind = kind
                    firmwareLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.padding(pad),
            )
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
                Text("行车记录仪", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (status.isConnected) "已发现 · ${status.ip}" else "正在通过车机热点发现设备…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(if (status.isConnected) "已连接" else "未连接") },
                leadingIcon = { Icon(if (status.isConnected) Icons.Default.CheckCircle else Icons.Default.Sync, null) }
            )
        }

        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            if (status.ip != null) {
                Box(Modifier.fillMaxWidth().background(Color(0xFF111316))) {
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
                            Icon(Icons.Default.FiberManualRecord, null, tint = Color.Red, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(status.recordingLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF111316)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("等待 DVR IP", color = Color.White)
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
                StatusRow("录音", when (status.mic) { true -> "开启"; false -> "关闭"; null -> "未知" })
                StatusRow("SD 卡", status.sdLabel)
                StatusRow("总容量", status.totalSize ?: "—")
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
private fun MediaScreen(
    status: DvrStatus,
    state: MediaUiState,
    message: String?,
    vm: DvrViewModel,
    modifier: Modifier,
) {
    var selectedFile by remember { mutableStateOf<DvrMediaFile?>(null) }
    var deleteTarget by remember { mutableStateOf<DvrMediaFile?>(null) }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.DeleteForever, null) },
            title = { Text("删除文件？") },
            text = { Text(file.name) },
            confirmButton = {
                Button(onClick = { vm.delete(file); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("记录文件", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.loadMedia(state.kind) }, enabled = status.isConnected) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    DvrMediaFile.Kind.NORMAL to "普通录像",
                    DvrMediaFile.Kind.EVENT to "事件录像",
                    DvrMediaFile.Kind.PARKING to "停车录像",
                    DvrMediaFile.Kind.PHOTO to "照片",
                ).forEach { (kind, label) ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = {
                            selectedFile = null
                            vm.loadMedia(kind)
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (state.kind == DvrMediaFile.Kind.PARKING) {
                Text(
                    "停车录像类型来自原厂 PARKING_VIDEO 与 /mnt/mmc/Parking；当前从 DCIM 返回中筛选停车目录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        selectedFile?.let { file ->
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { vm.download(file) }) { Icon(Icons.Default.Download, "下载") }
                        if (file.kind == DvrMediaFile.Kind.NORMAL || file.kind == DvrMediaFile.Kind.EVENT) {
                            IconButton(onClick = { vm.move(file) }) {
                                Icon(if (file.kind == DvrMediaFile.Kind.NORMAL) Icons.Default.Lock else Icons.Default.LockOpen, if (file.kind == DvrMediaFile.Kind.NORMAL) "锁定" else "解锁")
                            }
                        }
                        if (file.kind != DvrMediaFile.Kind.PARKING) {
                            IconButton(onClick = { deleteTarget = file }) { Icon(Icons.Default.Delete, "删除") }
                        }
                        IconButton(onClick = { selectedFile = null }) { Icon(Icons.Default.Close, "关闭") }
                    }

                    val url = vm.mediaUrl(file)
                    if (file.kind != DvrMediaFile.Kind.PHOTO && url != null) {
                        HttpVideoPlayer(url)
                    } else if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 520.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.loadMedia(state.kind) }) { Text("重新加载") }
                }
            }
            state.files.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (state.kind == DvrMediaFile.Kind.PARKING) "当前列表中未发现停车录像" else "未解析到文件")
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.files, key = { it.remotePath }) { file ->
                    ElevatedCard(
                        Modifier.fillMaxWidth().clickable { selectedFile = file },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                if (file.kind == DvrMediaFile.Kind.PHOTO) {
                                    vm.mediaUrl(file)?.let { url ->
                                        AsyncImage(model = url, contentDescription = file.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(if (file.kind == DvrMediaFile.Kind.PARKING) Icons.Default.LocalParking else Icons.Default.Movie, null)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(file.remotePath, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.download(file) }) { Icon(Icons.Default.Download, "下载") }
                            if (file.kind == DvrMediaFile.Kind.NORMAL || file.kind == DvrMediaFile.Kind.EVENT) {
                                IconButton(onClick = { vm.move(file) }) {
                                    Icon(if (file.kind == DvrMediaFile.Kind.NORMAL) Icons.Default.Lock else Icons.Default.LockOpen, null)
                                }
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
private fun SettingsScreen(
    status: DvrStatus,
    message: String?,
    downloadDirectory: String?,
    vm: DvrViewModel,
    onChooseDownloadDirectory: () -> Unit,
    onChooseFirmware: (FirmwareKind) -> Unit,
    modifier: Modifier,
) {
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var firmwareKind by remember { mutableStateOf<FirmwareKind?>(null) }
    var appAuthValue by remember { mutableStateOf("") }
    var authTimeValue by remember { mutableStateOf("") }

    confirmAction?.let { action ->
        val formatting = action == "format"
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            icon = { Icon(if (formatting) Icons.Default.Warning else Icons.Default.Eject, null) },
            title = { Text(if (formatting) "格式化内存卡？" else "安全移除内存卡？") },
            text = {
                Text(if (formatting) "格式化会删除内存卡中的全部录像和照片，此操作不可撤销。" else "将发送原厂安全移除指令，完成后再物理拔卡。")
            },
            confirmButton = {
                Button(onClick = {
                    if (formatting) vm.formatSd() else vm.removeSd()
                    confirmAction = null
                }) { Text(if (formatting) "确认格式化" else "确认移除") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("取消") } }
        )
    }

    firmwareKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { firmwareKind = null },
            icon = { Icon(Icons.Default.SystemUpdate, null) },
            title = { Text("选择${if (kind == FirmwareKind.SOC) " SOC " else " MCU "}固件？") },
            text = { Text("刷写错误固件可能导致记录仪无法启动。仅使用与当前硬件完全匹配的原厂固件。") },
            confirmButton = {
                Button(onClick = {
                    onChooseFirmware(kind)
                    firmwareKind = null
                }) { Text("选择固件") }
            },
            dismissButton = { TextButton(onClick = { firmwareKind = null }) { Text("取消") } }
        )
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("录音", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("录像录音")
                        Text(
                            when (status.mic) { true -> "当前已开启"; false -> "当前已关闭"; null -> "等待设备状态" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = status.mic == true,
                        onCheckedChange = { vm.mic(it) },
                        enabled = status.isConnected && status.mic != null,
                    )
                }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("内存卡状态", style = MaterialTheme.typography.titleLarge)
                StatusRow("状态", status.sdLabel)
                StatusRow("总容量", status.totalSize ?: "—")
                StatusRow("普通录像占用", status.usedNormalSpace ?: "—")
                StatusRow("事件录像占用", status.usedEventSpace ?: "—")
                StatusRow("照片占用", status.usedPhotoSpace ?: "—")
            }
        }

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("下载", style = MaterialTheme.typography.titleLarge)
                Text(
                    downloadDirectory ?: "尚未选择目录",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onChooseDownloadDirectory, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (downloadDirectory == null) "选择下载目录" else "更改下载目录")
                }
                if (downloadDirectory != null) {
                    TextButton(onClick = { vm.setDownloadDirectory(null) }) { Text("清除目录设置") }
                }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("高级", style = MaterialTheme.typography.titleLarge)

                ListItem(
                    headlineContent = { Text("安全移除内存卡") },
                    supportingContent = { Text("原厂 action=rmove&property=sd") },
                    leadingContent = { Icon(Icons.Default.Eject, null) },
                    modifier = Modifier.clickable(enabled = status.isConnected) { confirmAction = "remove" }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("格式化内存卡") },
                    supportingContent = { Text("删除全部录像和照片") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, null) },
                    modifier = Modifier.clickable(enabled = status.isConnected) { confirmAction = "format" }
                )
            }
        }

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("原厂授权机制", style = MaterialTheme.typography.titleLarge)
                Text(
                    "原 APK 存在 setapp 与 AuthTime 接口，但具体 value 由原厂业务逻辑生成；这里作为高级兼容入口保留。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(appAuthValue, { appAuthValue = it }, label = { Text("setapp value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.authorizeApp(appAuthValue) }, enabled = status.isConnected && appAuthValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("发送 APP 授权") }
                OutlinedTextField(authTimeValue, { authTimeValue = it }, label = { Text("AuthTime value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.setAuthTime(authTimeValue) }, enabled = status.isConnected && authTimeValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("发送录音授权时效") }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("固件升级", style = MaterialTheme.typography.titleLarge)
                Text("复刻原厂 SOC / MCU 升级入口。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { firmwareKind = FirmwareKind.SOC }, enabled = status.isConnected, modifier = Modifier.fillMaxWidth()) {
                    Text("选择 SOC 固件并升级")
                }
                OutlinedButton(onClick = { firmwareKind = FirmwareKind.MCU }, enabled = status.isConnected, modifier = Modifier.fillMaxWidth()) {
                    Text("选择 MCU 固件并升级")
                }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("连接信息", style = MaterialTheme.typography.titleLarge)
                StatusRow("DVR IP", status.ip ?: "未发现")
                StatusRow("RTSP", status.ip?.let { DvrProtocol.rtsp(it) } ?: "—")
                StatusRow("发现端口", "UDP 49142 / 53296")
                StatusRow("视频传输", "RTSP / RTP-over-TCP")
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun QuickAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    action: () -> Unit,
) {
    FilledTonalButton(onClick = action, modifier = modifier.height(58.dp), shape = RoundedCornerShape(18.dp)) {
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
