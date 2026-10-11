package com.longhorn.dvr.worldgm

data class AdvancedDvrState(
    val loading: Boolean = false,
    val sigmaParkingMonitor: Boolean? = null,
    val sigmaGSensor: String? = null,
    val sigmaPowerOnGSensor: String? = null,
    val deviceIp: String? = null,
    val readAtMillis: Long? = null,
    val sigmaReadErrors: Map<String, String> = emptyMap(),
    val sigmaPending: Set<String> = emptySet(),
    val sigmaValues: Map<String, String> = emptyMap(),
    val lastRawResponse: String? = null,
    val error: String? = null,
)

object DvrResponseParser {
    fun xmlValue(raw: String): String? =
        Regex("""<Value>\s*([^<]+?)\s*</Value>""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()
}

