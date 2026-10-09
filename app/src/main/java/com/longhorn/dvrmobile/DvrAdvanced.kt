package com.longhorn.dvr.worldgm

enum class ParkingMode(val value: Int, val label: String) {
    TIMELAPSE(0, "延时停车录像"),
    NORMAL(1, "普通停车录像"),
    SLEEP(2, "睡眠监控"),
    POWER_OFF(3, "关闭停车监控"),
    SENTINEL(4, "Sentinel 驻车守卫");

    companion object {
        fun fromValue(value: Int?): ParkingMode? = entries.firstOrNull { it.value == value }
    }
}

data class AdvancedDvrState(
    val loading: Boolean = false,
    val aiActive: Boolean? = null,
    val aiEnabled: Boolean? = null,
    val parkingMode: ParkingMode? = null,
    val parkingGSensorLevel: Int? = null,
    val peopleRoi: String = "0,1080;0,0;1920,0;1920,1080",
    val peopleDetectDuration: Int? = null,
    val sigmaParkingMonitor: Boolean? = null,
    val sigmaGSensor: String? = null,
    val sigmaPowerOnGSensor: String? = null,
    val sigmaValues: Map<String, String> = emptyMap(),
    val ppgAvailable: Boolean? = null,
    val supportedPpgCommands: Set<Int> = emptySet(),
    val roiReadable: Boolean? = null,
    val sentinelReadable: Boolean? = null,
    val diagnosticResponses: Map<String, String> = emptyMap(),
    val lastRawResponse: String? = null,
    val error: String? = null,
) {
    val sentinelReady: Boolean
        get() = aiActive == true && aiEnabled != false && parkingMode == ParkingMode.SENTINEL
}

object DvrResponseParser {
    fun xmlValue(raw: String): String? =
        Regex("""<Value>\s*([^<]+?)\s*</Value>""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()

    fun xmlStatus(raw: String): String? =
        Regex("""<Status>\s*([^<]+?)\s*</Status>""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()

    fun firstInt(raw: String): Int? =
        Regex("""-?\d+""").find(raw)?.value?.toIntOrNull()

    fun ppgInt(raw: String): Int? {
        xmlValue(raw)?.toIntOrNull()?.let { return it }
        xmlStatus(raw)?.toIntOrNull()?.let { return it }

        // Do not treat XML declaration version="1.0" or <Cmd>xxxx</Cmd>
        // as the setting value when the response does not actually expose one.
        if ('<' in raw && '>' in raw) return null
        return firstInt(raw)
    }

    fun isPpgResponse(raw: String?, expectedCmd: Int? = null): Boolean {
        if (raw.isNullOrBlank()) return false
        if (!raw.contains("<Function", ignoreCase = true)) return false
        if (!raw.contains("<Cmd>", ignoreCase = true)) return false
        return expectedCmd == null ||
            Regex("""<Cmd>\s*${expectedCmd}\s*</Cmd>""", RegexOption.IGNORE_CASE).containsMatchIn(raw)
    }

    fun commandNumbers(raw: String): Set<Int> =
        Regex("""(?<!\d)(\d{4})(?!\d)""")
            .findAll(raw)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1000..9999 }
            .toSet()

    fun ppgCommandMap(raw: String): Map<Int, Pair<Int?, String?>> {
        val result = linkedMapOf<Int, Pair<Int?, String?>>()
        val blockRegex = Regex(
            """<Cmd>\s*(\d+)\s*</Cmd>(.*?)(?=<Cmd>|</Function>|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        blockRegex.findAll(raw).forEach { match ->
            val cmd = match.groupValues[1].toIntOrNull() ?: return@forEach
            val body = match.groupValues[2]
            val status = Regex(
                """<Status>\s*([^<]+?)\s*</Status>""",
                RegexOption.IGNORE_CASE
            ).find(body)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull()
            val value = Regex(
                """<Value>\s*([^<]+?)\s*</Value>""",
                RegexOption.IGNORE_CASE
            ).find(body)?.groupValues?.getOrNull(1)?.trim()
            result[cmd] = status to value
        }
        return result
    }

    fun normalizeRoi(raw: String): String? {
        val value = xmlValue(raw) ?: raw
        val match = Regex(
            """\d+\s*,\s*\d+(?:\s*;\s*\d+\s*,\s*\d+){3,5}"""
        ).find(value) ?: return null
        return match.value.replace(Regex("""\s+"""), "")
    }

    fun isValidRoi(roi: String): Boolean {
        val points = roi.split(';')
        if (points.size !in 4..6) return false
        return points.all { point ->
            val xy = point.split(',')
            if (xy.size != 2) return@all false
            val x = xy[0].trim().toIntOrNull() ?: return@all false
            val y = xy[1].trim().toIntOrNull() ?: return@all false
            x in 0..1920 && y in 0..1080
        }
    }
}
