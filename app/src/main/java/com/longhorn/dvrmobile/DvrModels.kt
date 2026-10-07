package com.longhorn.dvr.worldgm

data class DvrStatus(
    val ip: String? = null,
    val socVersion: String? = null,
    val innerVersion: String? = null,
    val mcuVersion: String? = null,
    val record: String? = null,
    val sd: String? = null,
    val totalSize: String? = null,
    val usedNormalSpace: String? = null,
    val usedEventSpace: String? = null,
    val usedPhotoSpace: String? = null,
    val eventStatus: String? = null,
    val mic: Boolean? = null,
    val dvrEnabled: Boolean? = null,
    val rtspFlag: String? = null,
    val dvrModel: String? = null,
    val lastMessage: String? = null,
) {
    val isConnected: Boolean get() = !ip.isNullOrBlank()
    val recordingLabel: String get() = when (record) {
        "1" -> "普通录像中"
        "2" -> "事件录像中"
        else -> "未录像"
    }
    val sdLabel: String get() = when (sd) {
        "0" -> "正常"
        "1" -> "未插入"
        "2", "7" -> "写入异常"
        "3" -> "读取异常"
        else -> "未知"
    }

    val totalSizeMb: String get() = storageMb(totalSize)
    val usedNormalSpaceMb: String get() = storageMb(usedNormalSpace)
    val usedEventSpaceMb: String get() = storageMb(usedEventSpace)
    val usedPhotoSpaceMb: String get() = storageMb(usedPhotoSpace)

    private fun storageMb(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val text = raw.trim()
        val number = Regex("""[-+]?\d+(?:\.\d+)?""").find(text)?.value?.toDoubleOrNull()
            ?: return raw
        val upper = text.uppercase()

        val mb = when {
            "GB" in upper -> number * 1024.0
            "MB" in upper -> number
            "KB" in upper -> number / 1024.0
            upper.endsWith("B") -> number / (1024.0 * 1024.0)
            else -> number / inferredStorageDivisor()
        }

        return if (mb >= 100.0) {
            String.format(java.util.Locale.US, "%.0f MB", mb)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }

    private fun inferredStorageDivisor(): Double {
        val total = totalSize
            ?.let { Regex("""[-+]?\d+(?:\.\d+)?""").find(it)?.value?.toDoubleOrNull() }
            ?: return 1.0
        return when {
            total >= 1024.0 * 1024.0 * 1024.0 -> 1024.0 * 1024.0
            total >= 1024.0 * 1024.0 -> 1024.0
            else -> 1.0
        }
    }
}

data class DvrMediaFile(
    val remotePath: String,
    val name: String,
    val kind: Kind,
) {
    enum class Kind { NORMAL, EVENT, PARKING, PHOTO }
}
