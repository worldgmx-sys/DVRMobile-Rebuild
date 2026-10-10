package com.longhorn.dvr.worldgm

import org.json.JSONObject

data class DvrSettingsBackup(
    val formatVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val deviceModel: String? = null,
    val socVersion: String? = null,
    val mcuVersion: String? = null,
    val dvrEnabled: Boolean? = null,
    val micEnabled: Boolean? = null,
    val sigma: Map<String, String> = emptyMap(),
    val aiEnabled: Boolean? = null,
    val parkingMode: Int? = null,
    val parkingGSensor: Int? = null,
    val peopleRoi: String? = null,
    val peopleDetectDuration: Int? = null,
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("formatVersion", formatVersion)
        root.put("createdAt", createdAt)
        root.put("appVersion", appVersion)

        val device = JSONObject()
        device.putOpt("model", deviceModel)
        device.putOpt("socVersion", socVersion)
        device.putOpt("mcuVersion", mcuVersion)
        device.putOpt("dvrEnabled", dvrEnabled)
        device.putOpt("micEnabled", micEnabled)
        root.put("device", device)

        val sigmaObject = JSONObject()
        sigma.toSortedMap().forEach { (key, value) -> sigmaObject.put(key, value) }
        root.put("sigma", sigmaObject)


        return root.toString(2)
    }

    companion object {
        fun fromJson(text: String): DvrSettingsBackup {
            val root = JSONObject(text)
            require(root.optInt("formatVersion", -1) == 1) { "不支持的备份格式版本" }

            val device = root.optJSONObject("device") ?: JSONObject()
            val sigmaObject = root.optJSONObject("sigma") ?: JSONObject()
            val sigma = linkedMapOf<String, String>()
            val keys = sigmaObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = sigmaObject.optString(key, "")
                if (value.isNotBlank()) sigma[key] = value
            }

            // Legacy backups can contain AStar fields; ignore them on SigmaStar S38.

            fun nullableBoolean(obj: JSONObject, key: String): Boolean? =
                if (obj.has(key) && !obj.isNull(key)) obj.optBoolean(key) else null

            fun nullableInt(obj: JSONObject, key: String): Int? =
                if (obj.has(key) && !obj.isNull(key)) obj.optInt(key) else null

            fun nullableString(obj: JSONObject, key: String): String? =
                if (obj.has(key) && !obj.isNull(key)) obj.optString(key).takeIf { it.isNotBlank() } else null

            return DvrSettingsBackup(
                formatVersion = 1,
                createdAt = root.optLong("createdAt", 0L),
                appVersion = root.optString("appVersion", "unknown"),
                deviceModel = nullableString(device, "model"),
                socVersion = nullableString(device, "socVersion"),
                mcuVersion = nullableString(device, "mcuVersion"),
                dvrEnabled = nullableBoolean(device, "dvrEnabled"),
                micEnabled = nullableBoolean(device, "micEnabled"),
                sigma = sigma,
                aiEnabled = null,
                parkingMode = null,
                parkingGSensor = null,
                peopleRoi = null,
                peopleDetectDuration = null,
            )
        }
    }
}

data class RestoreReport(
    val applied: Int,
    val skipped: Int,
    val failed: Int,
    val restartMayBeRequired: Boolean,
    val details: List<String>,
) {
    val summary: String
        get() = "恢复完成：成功 $applied 项，跳过 $skipped 项，失败 $failed 项" +
            if (restartMayBeRequired) "；部分设置可能需要设备重启" else ""
}

object DvrBackupCatalog {
    val sigmaReadableProperties = listOf(
        "VideoRes", "LoopingVideo", "MotionDetect", "MotionVideoTime",
        "LDWS", "FCWS", "SAG", "NightMode", "WNR", "HDR",
        "SlowMotion", "Timelapse", "AutoRec", "VideoPreRecord",
        "MicSensitivity", "VideoQuality", "VoiceSwitch", "Flicker",
        "ISO", "AWB", "EV", "DateLogoStamp", "GpsStamp", "SpeedStamp",
        "Brightness", "Contrast", "Saturation", "Sharpness",
        "ParkingMonitor", "GSensor"
    )

    val sigmaWritableProperties = sigmaReadableProperties.toSet()
}
