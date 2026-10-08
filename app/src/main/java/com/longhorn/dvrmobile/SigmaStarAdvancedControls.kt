package com.longhorn.dvr.worldgm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SigmaStarAdvancedControls(
    enabled: Boolean,
    vm: DvrViewModel,
    modifier: Modifier = Modifier,
) {
    val advanced by vm.advanced.collectAsStateWithLifecycle()
    val current = advanced.sigmaValues

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SigmaStar 影像与高级参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "以下取值来自原厂 CGI_PROCESS.sh；仅在 SigmaStar 平台生效。AStar/其他平台不支持时会返回错误。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        ChoiceSetting("视频分辨率", "VideoRes", listOf(
            "2160P25fps", "1440P30fps", "1080P30fps",
            "1080P27.5fpsHDR", "720P30fps", "720P27.5fpsHDR", "720P60fps"
        ), current["VideoRes"], enabled, vm)

        ChoiceSetting("循环录像", "LoopingVideo",
            listOf("OFF", "1MIN", "2MIN", "3MIN", "5MIN", "10MIN", "15MIN"), current["LoopingVideo"], enabled, vm)

        ChoiceSetting("移动侦测", "MotionDetect",
            listOf("OFF", "LOW", "MID", "HIGH"), current["MotionDetect"], enabled, vm)

        ChoiceSetting("移动侦测录像时长", "MotionVideoTime",
            listOf("5", "10", "30", "60"), current["MotionVideoTime"], enabled, vm)

        ToggleChoice("HDR", "HDR", current["HDR"], enabled, vm)
        ToggleChoice("WNR 风噪/降噪", "WNR", current["WNR"], enabled, vm)
        ToggleChoice("夜间模式", "NightMode", current["NightMode"], enabled, vm)
        ToggleChoice("LDWS 车道偏离预警", "LDWS", current["LDWS"], enabled, vm)
        ToggleChoice("FCWS 前碰撞预警", "FCWS", current["FCWS"], enabled, vm)
        ToggleChoice("SAG 前车起步提醒", "SAG", current["SAG"], enabled, vm)
        ToggleChoice("自动录像", "AutoRec", current["AutoRec"], enabled, vm)
        ToggleChoice("预录", "VideoPreRecord", current["VideoPreRecord"], enabled, vm)
        ToggleChoice("语音提示", "VoiceSwitch", current["VoiceSwitch"], enabled, vm)
        ToggleChoice("GPS 水印", "GpsStamp", current["GpsStamp"], enabled, vm)
        ToggleChoice("速度水印", "SpeedStamp", current["SpeedStamp"], enabled, vm)

        ChoiceSetting("慢动作", "SlowMotion",
            listOf("X1", "X2", "X4", "X8"), current["SlowMotion"], enabled, vm)

        ChoiceSetting("延时录像间隔", "Timelapse",
            listOf("OFF", "1SEC", "5SEC", "10SEC", "30SEC", "60SEC"), current["Timelapse"], enabled, vm)

        ChoiceSetting("麦克风灵敏度", "MicSensitivity",
            listOf("STANDARD", "LOW"), current["MicSensitivity"], enabled, vm)

        ChoiceSetting("录像画质", "VideoQuality",
            listOf("SUPER_FINE", "FINE"), current["VideoQuality"], enabled, vm)

        ChoiceSetting("防闪烁", "Flicker",
            listOf("50HZ", "60HZ"), current["Flicker"], enabled, vm)

        ChoiceSetting("ISO", "ISO",
            listOf("ISO_AUTO", "ISO_100", "ISO_200", "ISO_400", "ISO_800", "ISO_1600", "ISO_3200"),
            current["ISO"], enabled, vm)

        ChoiceSetting("白平衡", "AWB",
            listOf("Auto", "Daylight", "Cloudy", "Fluorescent1", "Fluorescent2", "Fluorescent3", "Incandescent"),
            current["AWB"], enabled, vm)

        ChoiceSetting("曝光补偿", "EV",
            listOf(
                "EVN200", "EVN167", "EVN133", "EVN100", "EVN67", "EVN33",
                "EV0",
                "EVP33", "EVP67", "EVP100", "EVP133", "EVP167", "EVP200"
            ),
            current["EV"], enabled, vm)

        ChoiceSetting("日期/Logo 水印", "DateLogoStamp",
            listOf("DATELOGO", "DATE", "LOGO", "OFF"), current["DateLogoStamp"], enabled, vm)

        NumericSetting("亮度", "Brightness", 0, 100, current["Brightness"], enabled, vm)
        NumericSetting("对比度", "Contrast", 0, 100, current["Contrast"], enabled, vm)
        NumericSetting("饱和度", "Saturation", 0, 127, current["Saturation"], enabled, vm)
        NumericSetting("锐度", "Sharpness", 0, 1023, current["Sharpness"], enabled, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceSetting(
    title: String,
    property: String,
    values: List<String>,
    selectedValue: String?,
    enabled: Boolean,
    vm: DvrViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = selectedValue?.equals(value, ignoreCase = true) == true,
                    onClick = { vm.setSigmaProperty(property, value, title) },
                    enabled = enabled,
                    label = { Text(value) }
                )
            }
        }
    }
}

@Composable
private fun ToggleChoice(
    title: String,
    property: String,
    selectedValue: String?,
    enabled: Boolean,
    vm: DvrViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        FilterChip(
            selected = selectedValue?.equals("ON", true) == true || selectedValue == "1" || selectedValue?.equals("ENABLE", true) == true,
            onClick = { vm.setSigmaProperty(property, "ON", title) },
            enabled = enabled,
            label = { Text("开") }
        )
        FilterChip(
            selected = selectedValue?.equals("OFF", true) == true || selectedValue == "0" || selectedValue?.equals("DISABLE", true) == true,
            onClick = { vm.setSigmaProperty(property, "OFF", title) },
            enabled = enabled,
            label = { Text("关") }
        )
    }
}

@Composable
private fun NumericSetting(
    title: String,
    property: String,
    min: Int,
    max: Int,
    selectedValue: String?,
    enabled: Boolean,
    vm: DvrViewModel,
) {
    var text by remember(selectedValue) { mutableStateOf(selectedValue.orEmpty()) }
    val value = text.toIntOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.weight(1f),
            label = { Text("$title ($min–$max)") },
            singleLine = true,
        )
        Button(
            onClick = { value?.let { vm.setSigmaProperty(property, it.toString(), title) } },
            enabled = enabled && value != null && value in min..max,
        ) {
            Text("设置")
        }
    }
}
