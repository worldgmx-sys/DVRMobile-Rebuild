package com.longhorn.dvr.worldgm

/** Only device responses populate current values. Never infer inverse 0/1 enums. */
object SigmaReadback {
    val getters = linkedMapOf(
        "VideoRes" to "Videores", "LoopingVideo" to "VideoClipTime",
        "Brightness" to "Brightness", "Contrast" to "Contrast", "Hue" to "Hue",
        "Saturation" to "Saturation", "Sharpness" to "Sharpness", "Gamma" to "Gamma",
        "EV" to "EV", "AE" to "AE", "Flicker" to "Flicker", "AWB" to "AWB",
        "Shutter" to "Shutter", "StillBurstShot" to "StillBurstShot",
        "LDWS" to "LDWS", "FCWS" to "FCWS", "SAG" to "SAG",
        "NightMode" to "NightMode", "WNR" to "WNR", "HDR" to "HDR",
        "SlowMotion" to "SlowMotion", "Timelapse" to "Timelapse", "AutoRec" to "AutoRec",
        "VideoOffTime" to "VideoOffTime", "VideoPreRecord" to "VideoPreRecord",
        "MicSensitivity" to "MicSensitivity", "VideoQuality" to "VideoQuality",
        "SoundRecord" to "SoundRecord", "ParkingMonitor" to "ParkingMonitor",
        "MotionVideoTime" to "MotionVideoTime", "PlaybackVolume" to "PlaybackVolume",
        "Beep" to "Beep", "AutoPowerOff" to "AutoPowerOff",
        "DateTimeFormat" to "DateTimeFormat", "DateLogoStamp" to "DateLogoStamp",
        "GpsStamp" to "GpsStamp", "SpeedStamp" to "SpeedStamp", "Language" to "Language",
        "UsbFunction" to "UsbFunction", "LcdPowerSave" to "LcdPowerSave",
        "GSensor" to "GSensor", "PowerOnGSensor" to "PowerOnGSensor",
        "MotionDetect" to "MotionDetect", "TimeZone" to "TimeZone",
        "Net.WIFI_AP.SSID" to "Net.WIFI_AP.SSID",
        "Net.WIFI_STA.AP.2.SSID" to "Net.WIFI_STA.AP.2.SSID"
    )

    fun parse(raw: String, property: String): String? {
        val text = raw.trim()
        if (text.isBlank() || text.length > 4096) return null
        if (Regex("""(?i)\b(nvconf\s+get|unsupported|not\s+support|unknown\s+property|usage|error|failed|fail|congratulations)\b""").containsMatchIn(text)) return null
        if (Regex("""(?i)<(?:!doctype\s+html|html|body)\b""").containsMatchIn(text)) return null
        fun valid(value: String): String? = value.trim().trim('"', '\'').takeIf {
            it.isNotBlank() && it.length <= 96 && !it.any { c -> c.isISOControl() } &&
                '<' !in it && '>' !in it &&
                !it.equals("OK", true) && !it.equals("SUCCESS", true) &&
                !it.equals("UNKNOWN", true) && !it.equals("NULL", true)
        }
        val keyed = Regex("""^\s*${Regex.escape(property)}\s*[:=]\s*(.*?)\s*$""", RegexOption.IGNORE_CASE)
            .findAll(text).toList()
        if (keyed.size == 1) return valid(keyed.single().groupValues[1])
        val xml = Regex("""<Value>\s*([^<]*?)\s*</Value>""", RegexOption.IGNORE_CASE).findAll(text).toList()
        if (xml.size == 1) return valid(xml.single().groupValues[1])
        // Plain scalar responses only: reject key/value echoes and arbitrary prose.
        if (Regex("""[A-Za-z0-9_+./:-]+""").matches(text) && '=' !in text) return valid(text)
        return null
    }

    fun matches(current: String?, choice: String): Boolean = current?.trim()?.equals(choice, true) == true
}
