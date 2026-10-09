package com.longhorn.dvr.worldgm

import java.net.URLEncoder

object DvrProtocol {
    fun base(ip: String) = "http://$ip"
    fun rtsp(ip: String) = "rtsp://$ip/liveRTSP/av0"

    fun getDvr(ip: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=get&property=DVR"

    fun setDvr(ip: String, on: Boolean) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=DVR&value=${if (on) "ON" else "OFF"}"

    fun setMic(ip: String, on: Boolean) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=mic&value=${if (on) "ON" else "OFF"}"

    fun video(ip: String, value: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=Video&value=$value"

    fun removeSd(ip: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=rmove&property=sd"

    fun formatSd(ip: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=format&property=format"

    fun setApp(ip: String, value: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=setapp&value=$value"

    fun setAuthTime(ip: String, value: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=AuthTime&value=$value"

    fun configGet(ip: String, property: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=get&property=$property"

    fun configSet(ip: String, property: String, value: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=$property&value=${encode(value)}"

    /** AStar/Hi3516 PPG/Papago compatibility endpoint recovered from dvr_main. */
    fun ppg(ip: String, cmd: Int, par: String? = null, str: String? = null): String {
        val params = mutableListOf(
            "custom=1",
            "cmd=$cmd",
        )
        par?.let { params += "par=${encode(it)}" }
        str?.let { params += "str=${encode(it)}" }
        return "${base(ip)}/?${params.joinToString("&")}"
    }

    fun ppgSupportedCommands(ip: String) = ppg(ip, 3002)
    fun ppgStatusAll(ip: String) = ppg(ip, 3014)
    fun ppgSaveSettings(ip: String) = ppg(ip, 3021)
    fun ppgOptions(ip: String) = ppg(ip, 3031)
    fun ppgRead(ip: String, cmd: Int) = ppg(ip, cmd)
    fun aiActiveTest(ip: String) = ppg(ip, 9023)
    fun setAlgEnabled(ip: String, enabled: Boolean) = ppg(ip, 9096, par = if (enabled) "1" else "0")
    fun setPeopleRoi(ip: String, roi: String) = ppg(ip, 9097, str = roi)
    fun getPeopleRoi(ip: String) = ppg(ip, 9098)
    fun setPeopleDetectDuration(ip: String, value: Int) = ppg(ip, 9099, par = value.toString())
    fun setParkingGSensorAStar(ip: String, level: Int) = ppg(ip, 9106, par = level.toString())
    fun setParkingModeAStar(ip: String, mode: Int) = ppg(ip, 9137, par = mode.toString())

    /** SigmaStar Config.cgi compatibility controls recovered from CGI_PROCESS.sh. */
    fun setParkingMonitor(ip: String, enabled: Boolean) =
        configSet(ip, "ParkingMonitor", if (enabled) "ENABLE" else "DISABLE")

    fun setGSensor(ip: String, value: String) = configSet(ip, "GSensor", value)
    fun setPowerOnGSensor(ip: String, value: String) = configSet(ip, "PowerOnGSensor", value)

    private fun encodePath(remotePath: String): String =
        remotePath.replace('/', 36.toChar())

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    fun delete(ip: String, remotePath: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=del&property=${encodePath(remotePath)}"

    fun move(ip: String, remotePath: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=mv&property=${encodePath(remotePath)}"

    fun list(ip: String, property: String, from: Int = 0) =
        "${base(ip)}/cgi-bin/Config.cgi?action=dir&property=$property&format=all&count=50000&from=$from"

    fun file(ip: String, path: String) =
        "${base(ip)}$path"

    fun thumbnail(ip: String, path: String) =
        "${base(ip)}/thumb$path"

    fun prepareSocUpgrade(ip: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=socupgrade&value=ON"

    fun prepareMcuUpgrade(ip: String) =
        "${base(ip)}/cgi-bin/Config.cgi?action=set&property=mcuupgrade&value=ON"

    fun upload(ip: String) =
        "${base(ip)}/action/upload"
}
