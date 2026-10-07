package com.longhorn.dvrmobile

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
}

data class DvrMediaFile(
    val remotePath: String,
    val name: String,
    val kind: Kind,
) {
    enum class Kind { NORMAL, EVENT, PARKING, PHOTO }
}
