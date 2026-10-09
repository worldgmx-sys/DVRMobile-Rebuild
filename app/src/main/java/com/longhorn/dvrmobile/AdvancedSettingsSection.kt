package com.longhorn.dvr.worldgm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsSection(
    status: DvrStatus,
    vm: DvrViewModel,
    modifier: Modifier = Modifier,
) {
    val advanced by vm.advanced.collectAsStateWithLifecycle()
    var roiText by remember(advanced.peopleRoi) { mutableStateOf(advanced.peopleRoi) }
    var durationText by remember(advanced.peopleDetectDuration) {
        mutableStateOf(advanced.peopleDetectDuration?.toString().orEmpty())
    }
    var showAiConfirm by remember { mutableStateOf<Boolean?>(null) }

    val ppgKnown = advanced.supportedPpgCommands.isNotEmpty()
    val sentinelSupported = !ppgKnown || 9137 in advanced.supportedPpgCommands
    val roiWriteSupported = !ppgKnown || 9097 in advanced.supportedPpgCommands
    val roiReadSupported = !ppgKnown || 9098 in advanced.supportedPpgCommands

    showAiConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { showAiConfirm = null },
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text(if (target) "开启 AI 人员检测？" else "关闭 AI 人员检测？") },
            text = {
                Text(
                    if (target) {
                        "原厂固件会把 AI 开关写入 Flash，并可能立即重启 DVR。重连后可用“检测 AI 状态”确认授权是否成功。"
                    } else {
                        "关闭 AI 可能同时使 Sentinel 驻车守卫退出并回退到普通停车模式，设备可能立即重启。"
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.setAiEnabled(target)
                    showAiConfirm = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showAiConfirm = null }) { Text("取消") }
            }
        )
    }

    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("驻车与 Sentinel", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "来自原厂 SOC 固件的隐藏 PPG/CGI 控制。设备不支持时会返回错误，不强制模拟 MCU 车辆状态。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(
                    onClick = { vm.refreshAdvanced() },
                    enabled = status.isConnected && !advanced.loading
                ) {
                    Icon(Icons.Default.Refresh, "刷新高级状态")
                }
            }

            StatusPair("AI 授权/激活", when (advanced.aiActive) {
                true -> "已激活"
                false -> "未激活"
                null -> "未知"
            })
            StatusPair("AI 总开关", when (advanced.aiEnabled) {
                true -> "已开启"
                false -> "已关闭"
                null -> "未读回"
            })
            StatusPair("驻车模式", advanced.parkingMode?.label ?: "未读回")
            StatusPair("Sentinel", when {
                advanced.sentinelReady -> "已就绪"
                advanced.parkingMode == ParkingMode.SENTINEL && advanced.aiActive == false -> "AI 未激活"
                advanced.parkingMode == ParkingMode.SENTINEL -> "已设置，等待验证"
                else -> "未启用"
            })

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showAiConfirm = true },
                    enabled = status.isConnected,
                    modifier = Modifier.weight(1f)
                ) { Text("开启 AI") }
                OutlinedButton(
                    onClick = { showAiConfirm = false },
                    enabled = status.isConnected,
                    modifier = Modifier.weight(1f)
                ) { Text("关闭 AI") }
            }

            HorizontalDivider()
            Text("驻车模式", fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParkingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = advanced.parkingMode == mode,
                        onClick = { vm.setParkingMode(mode) },
                        enabled = status.isConnected && (mode != ParkingMode.SENTINEL || sentinelSupported),
                        label = {
                            Text(
                                if (mode == ParkingMode.SENTINEL && !sentinelSupported) {
                                    "${mode.label}（不支持）"
                                } else {
                                    mode.label
                                }
                            )
                        }
                    )
                }
            }

            Text(
                when {
                    !sentinelSupported -> "设备 3002 能力列表未声明 cmd=9137，因此当前固件不开放 Sentinel 停车模式设置。"
                    advanced.sentinelReadable == false -> "设备接受 PPG，但当前无法读回 9137 状态；设置后会执行 3021 保存并尝试直接读回验证。"
                    else -> "Sentinel 对应 cmd=9137&par=4；设置后自动执行 cmd=3021 保存，再读回验证。真正进入驻车状态仍由车辆 ACC/CAN/MCU 决定。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text("停车碰撞灵敏度（AStar）", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { level ->
                    FilterChip(
                        selected = advanced.parkingGSensorLevel == level,
                        onClick = { vm.setParkingGSensor(level) },
                        enabled = status.isConnected,
                        label = { Text(level.toString()) }
                    )
                }
            }

            HorizontalDivider()
            Text("人员检测区域 ROI", fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    !roiWriteSupported && !roiReadSupported -> "设备能力列表未声明 9097/9098，当前固件没有开放 ROI Web 接口。"
                    roiWriteSupported && !roiReadSupported -> "设备声明 9097 写入，但未声明 9098 读取：可尝试写入，但无法通过 Web 读回验证。"
                    else -> "使用 1920×1080 像素坐标，支持 4～6 个点。默认全画面：0,1080;0,0;1920,0;1920,1080"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = roiText,
                onValueChange = { roiText = it },
                label = { Text("ROI 坐标") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.loadPeopleRoi() },
                    enabled = status.isConnected && roiReadSupported,
                    modifier = Modifier.weight(1f)
                ) { Text(if (roiReadSupported) "读取 ROI" else "ROI 不可读") }
                Button(
                    onClick = { vm.setPeopleRoi(roiText.trim()) },
                    enabled = status.isConnected && roiWriteSupported && DvrResponseParser.isValidRoi(roiText.trim()),
                    modifier = Modifier.weight(1f)
                ) { Text(if (roiWriteSupported) "写入 ROI" else "ROI 不可写") }
            }

            HorizontalDivider()
            Text("人员检测持续时间", fontWeight = FontWeight.SemiBold)
            Text(
                "固件 cmd=9099；内部会在传入值基础上增加固定偏移。单位/最佳范围仍需实机验证。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                    label = { Text("参数 0–120") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { durationText.toIntOrNull()?.let(vm::setPeopleDetectDuration) },
                    enabled = status.isConnected && (durationText.toIntOrNull()?.let { it in 0..120 } == true),
                ) { Text("设置") }
            }

            HorizontalDivider()
            Text("SigmaStar 停车兼容", fontWeight = FontWeight.SemiBold)
            Text(
                "以下使用 Config.cgi 原厂接口；适用于 SigmaStar 平台，AStar 不支持时返回错误。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.setSigmaParkingMonitor(true) },
                    enabled = status.isConnected,
                    modifier = Modifier.weight(1f)
                ) { Text("开启停车监控") }
                OutlinedButton(
                    onClick = { vm.setSigmaParkingMonitor(false) },
                    enabled = status.isConnected,
                    modifier = Modifier.weight(1f)
                ) { Text("关闭") }
            }

            Text("行车 G-sensor", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("OFF", "LEVEL0", "LEVEL1", "LEVEL2", "LEVEL3", "LEVEL4").forEach { value ->
                    FilterChip(
                        selected = advanced.sigmaGSensor == value,
                        onClick = { vm.setSigmaGSensor(value) },
                        enabled = status.isConnected,
                        label = { Text(value) }
                    )
                }
            }

            Text("停车唤醒 G-sensor", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("OFF", "LEVEL0", "LEVEL1", "LEVEL2").forEach { value ->
                    FilterChip(
                        selected = advanced.sigmaPowerOnGSensor == value,
                        onClick = { vm.setSigmaPowerOnGSensor(value) },
                        enabled = status.isConnected,
                        label = { Text(value) }
                    )
                }
            }

            HorizontalDivider()
            SigmaStarAdvancedControls(
                enabled = status.isConnected,
                vm = vm,
            )

            advanced.lastRawResponse?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (advanced.diagnosticResponses.isNotEmpty()) {
                HorizontalDivider()
                Text("AStar 原始响应诊断", style = MaterialTheme.typography.labelLarge)
                advanced.diagnosticResponses.toSortedMap().forEach { (cmd, raw) ->
                    Text(
                        "cmd=$cmd\n$raw",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPair(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
