package com.longhorn.dvr.worldgm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class SigmaField(
    val label: String, val property: String,
    val values: List<String> = emptyList(),
    val hint: String = "",
    val experimental: Boolean = true, val risk: Boolean = false, val writable: Boolean = true
)

private data class SigmaCategory(val title: String, val fields: List<SigmaField>)

private fun f(
    name: String, key: String, vararg values: String, hint: String = "",
    risk: Boolean = false, writable: Boolean = true
) = SigmaField(name, key, values.toList(), hint, risk = risk, writable = writable)

private val catalog = listOf(
    SigmaCategory("视频录像与编码", listOf(
        f("视频分辨率", "VideoRes", "2160P25fps", "1440P30fps", "1080P30fps", "1080P27.5fpsHDR", "720P30fps", "720P27.5fpsHDR", "720P60fps", "VGA", hint="4K/HDR/高帧率档位未经过 S38 实机验证"),
        f("循环录像时长", "LoopingVideo", "1MIN", "2MIN", "3MIN", "5MIN", "10MIN", "15MIN", hint="实机已验证；原厂 OFF 实际映射为一分钟"),
        f("视频质量", "VideoQuality", "SUPER_FINE", "FINE"),
        f("视频码率", "setbitrate", hint="固件没有任何参数范围检查，也未确认单位；暂禁用任意写入", writable=false),
        f("自动录像", "AutoRec", "ON", "OFF", hint="固件命令存在反向编码"),
        f("预录", "VideoPreRecord", "ON", "OFF", hint="固件命令存在反向编码"),
        f("延时录像", "Timelapse", hint="当前未校验可用枚举，暂禁用任意输入", writable=false),
        f("慢动作", "SlowMotion", hint="当前未校验可用枚举，暂禁用任意输入", writable=false),
        f("自动停止录像时间", "VideoOffTime", hint="具体有效枚举需实机确认")
    )),
    SigmaCategory("图像与拍照", listOf(
        f("照片分辨率", "ImageRes", hint="原厂枚举与硬件能力需实机确认"),
        f("连拍数量", "StillBurstShot", hint="具体有效枚举需实机确认"),
        f("亮度", "Brightness", hint="数值范围依录像程序约束"),
        f("对比度", "Contrast"),
        f("色相", "Hue"),
        f("饱和度", "Saturation"),
        f("锐度", "Sharpness"),
        f("Gamma", "Gamma"),
        f("曝光补偿", "EV", "EVN200", "EVN167", "EVN133", "EVN100", "EVN67", "EVN33", "EV0", "EVP33", "EVP67", "EVP100", "EVP133", "EVP167", "EVP200"),
        f("自动曝光", "AE", hint="CGI 将 AE 写入 ISO 配置键，可能覆盖 ISO，暂禁用", writable=false),
        f("ISO", "ISO", "ISO_AUTO", "ISO_100", "ISO_200", "ISO_400", "ISO_800", "ISO_1600", "ISO_3200"),
        f("图像效果", "Effect"),
        f("防闪烁", "Flicker", "50HZ", "60HZ"),
        f("白平衡", "AWB", "Auto", "Daylight", "Cloudy", "Fluorescent1", "Fluorescent2", "Fluorescent3", "Incandescent"),
        f("快门", "Shutter"),
        f("HDR", "HDR", "ON", "OFF"),
        f("夜间模式", "NightMode", "ON", "OFF")
    )),
    SigmaCategory("音频", listOf(
        f("录像录音", "SoundRecord", "ON", "OFF", hint="固件内部 0/1 反向编码"),
        f("麦克风灵敏度", "MicSensitivity", "STANDARD", "LOW"),
        f("风噪抑制", "WNR", "ON", "OFF"),
        f("播放音量", "PlaybackVolume"),
        f("提示音", "Beep", "ON", "OFF"),
        f("语音提示", "VoiceSwitch", "ON", "OFF")
    )),
    SigmaCategory("停车、碰撞与移动侦测", listOf(
        f("停车监控", "ParkingMonitor", "ENABLE", "DISABLE"),
        f("行车碰撞检测", "GSensor", "OFF", "LEVEL0", "LEVEL1", "LEVEL2", "LEVEL3", "LEVEL4"),
        f("移动侦测", "MotionDetect", "OFF", "LOW", "MID", "HIGH"),
        f("移动侦测录像时长", "MotionVideoTime", "5", "10", "30", "60")
    )),
    SigmaCategory("驾驶辅助、水印与位置", listOf(
        f("车道偏离预警", "LDWS", "ON", "OFF"),
        f("前车碰撞预警", "FCWS", "ON", "OFF"),
        f("前车起步提醒", "SAG", "ON", "OFF"),
        f("GPS 水印", "GpsStamp", "ON", "OFF"),
        f("速度水印", "SpeedStamp", "ON", "OFF"),
        f("录像水印", "RecStamp", "ON", "OFF", hint="脚本写入 DateTimeFormat，存在持久化冲突，暂禁用", writable=false),
        f("日期与徽标水印", "DateLogoStamp", "DATELOGO", "DATE", "LOGO", "OFF"),
        f("时间日期格式", "DateTimeFormat", hint="与 RecStamp 共用持久化键；需实机核实", writable=false),
        f("速度单位", "SpeedUint"),
        f("测速摄像头提醒", "SpeedCamAlert", "ON", "OFF"),
        f("超速提醒", "SpeedLimitAlert", "ON", "OFF")
    )),
    SigmaCategory("Wi-Fi 与网络（可能导致断联）", listOf(
        f("热点名称 SSID", "Net.WIFI_AP.SSID", hint="修改后车机可能断联，请确认新 SSID", risk=true),
        f("热点密码", "Net.WIFI_AP.CryptoKey", hint="密码输入后不会展示在状态信息中；修改会导致车机断联", risk=true),
        f("STA 目标网络名称", "Net.WIFI_STA.AP.2.SSID", risk=true),
        f("STA 目标网络密码", "Net.WIFI_STA.AP.2.CryptoKey", risk=true),
        f("AP/STA 模式切换", "Net.WIFI_STA.AP.Switch",
            "ENABLE", hint="可能改变设备可访问网络，需备好恢复方式", risk=true)
    )),
    SigmaCategory("原厂维护与危险操作（仅列出能力）", listOf(
        f("停车唤醒 G-sensor", "PowerOnGSensor",
            hint="和停车监控共用 park FIFO 指令，存在覆盖风险", writable=false),
        f("设置 MJPEG 时间戳", "Camera.Preview.MJPEG.TimeStamp",
            hint="原厂使用特殊时间格式，未确认前不执行", writable=false),
        f("网络重置", "Net", hint="会中断设备网络连接", writable=false),
        f("设备录像控制", "Video",
            hint="开始、停止、事件录像和拍照已放在实时页面", writable=false),
        f("关机/供电控制", "Camera.System.Power",
            hint="可能影响停车监控和电源管理", writable=false),
        f("恢复出厂设置", "FactoryReset",
            hint="会清除设备配置，需独立操作确认", writable=false),
        f("重启设备", "reboot", hint="可能中断正在写入的录像", writable=false),
        f("SD 卡格式化", "SD0",
            hint="格式化功能已在设置页面单独提供确认对话框", writable=false),
        f("重新加载录像配置", "Setting",
            hint="可能停止并重新开始录像", writable=false),
        f("位置数据新增", "PosSetting_Add",
            hint="输入结构未知，暂不发送数据", writable=false),
        f("删除最后一条位置数据", "PosSetting_DelLast",
            hint="不可撤销，暂不开放", writable=false),
        f("清空位置数据", "PosSetting_DelAll",
            hint="不可撤销，暂不开放", writable=false),
        f("系统重启另一接口", "RebootSystem",
            hint="与 reboot 类似，暂不重复开放", writable=false)
    )),
    SigmaCategory("屏幕、系统与时间", listOf(
        f("语言", "Language", hint="需使用设备已有语言枚举"),
        f("LCD 亮度", "LCDBrightness", hint="无屏设备可能不生效"),
        f("LCD 节电", "LcdPowerSave", hint="无屏设备可能不生效"),
        f("自动关机", "AutoPowerOff"),
        f("USB 模式", "UsbFunction", hint="可能改变 USB 连接功能"),
        f("时区", "TimeZone"),
        f("同步时间", "SyncTime", hint="须按原厂支持的格式输入"),
        f("手动时间", "TimeSettings", hint="须按原厂支持的格式输入")
    ))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SigmaStarAdvancedControls(enabled: Boolean, vm: DvrViewModel, modifier: Modifier = Modifier) {
    val advanced by vm.advanced.collectAsStateWithLifecycle()
    val current = advanced.sigmaValues
    var showExperimental by remember { mutableStateOf(true) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置仅供 SigmaStar S38 设备使用。除循环录像外，多数功能尚未实机验证。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showExperimental, onClick = { showExperimental = !showExperimental },
                label = { Text(if (showExperimental) "显示全部实验性设置" else "显示已确认设置") })
        }
        catalog.forEach { group ->
            var expanded by remember(group.title) { mutableStateOf(group.title == "视频录像与编码") }
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text("${group.title}（${group.fields.size} 项）${if (expanded) " ▲" else " ▼"}")
                    }
                    if (expanded) {
                        group.fields.filter { showExperimental || it.property == "LoopingVideo" }.forEach { field ->
                            SigmaSettingField(
                                field = field, selected = current[field.property], enabled = enabled, vm = vm
                            )
                            HorizontalDivider()
                        }
                        if (!showExperimental && group.fields.none { it.property == "LoopingVideo" }) {
                            Text("开启“显示全部实验性设置”后可查看", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Text("高风险功能：停车唤醒 G-sensor、Wi-Fi 密码与模式、SD 格式化、复位、重启、电源管理和位置数据删除未作为普通一键设置开放。避免误操作导致车机断联、录像中断或数据丢失。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SigmaSettingField(field: SigmaField, selected: String?, enabled: Boolean, vm: DvrViewModel) {
    var text by remember(field.property) { mutableStateOf("") }
    var pendingValue by remember(field.property) { mutableStateOf<String?>(null) }
    val isSecret = field.property.contains("CryptoKey")
    fun submit(value: String) {
        if (field.risk) pendingValue = value else vm.setSigmaProperty(field.property, value, field.label)
    }
    pendingValue?.let { value ->
        AlertDialog(
            onDismissRequest = { pendingValue = null },
            title = { Text("确认修改 ${field.label}？") },
            text = { Text("该操作可能使车机与记录仪断开连接。请确认已准备好重新连接，并且参数填写正确。") },
            confirmButton = {
                Button(onClick = {
                    vm.setSigmaProperty(field.property, value, field.label)
                    pendingValue = null
                }) { Text("确认发送") }
            },
            dismissButton = { TextButton(onClick = { pendingValue = null }) { Text("取消") } }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(field.label, style = MaterialTheme.typography.labelLarge)
        Text("CGI: ${field.property}" + (selected?.let { " · 设备读回：$it" } ?: " · 设备值未验证"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (field.hint.isNotEmpty()) Text(field.hint, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!field.writable) {
            Text("仅展示固件能力；该操作目前未启用", style = MaterialTheme.typography.bodySmall)
        } else if (field.values.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                field.values.forEach { value ->
                    FilterChip(selected = selected?.equals(value, ignoreCase = true) == true,
                        onClick = { submit(value) },
                        enabled = enabled, label = { Text(value) })
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(64) },
                    label = { Text("参数值") }, singleLine = true, modifier = Modifier.weight(1f),
                    visualTransformation = if (isSecret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
                Button(onClick = { submit(text.trim()) },
                    enabled = enabled && text.isNotBlank()) { Text("发送") }
            }
        }
    }
}
