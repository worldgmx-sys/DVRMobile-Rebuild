package com.longhorn.dvr.worldgm

import java.net.URLEncoder

object DvrProtocol {
    fun base(ip: String) = "http://$ip"
    fun baseOnPort(ip: String, port: Int) =
        if (port == 80) "http://" + ip else "http://" + ip + ":" + port

    fun nativeCgi(ip: String, port: Int, path: String, query: String? = null): String {
        val normalized = if (path.startsWith("/")) path else "/" + path
        return baseOnPort(ip, port) + normalized + if (query.isNullOrBlank()) "" else "?" + query
    }

    fun nativeGetDeviceAttr(ip: String, port: Int) =
        nativeCgi(ip, port, "getdeviceattr.cgi")

    fun nativeGetWorkState(ip: String, port: Int) =
        nativeCgi(ip, port, "getworkstate.cgi")

    fun nativeGetWorkMode(ip: String, port: Int) =
        nativeCgi(ip, port, "getworkmodecmd.cgi")

    fun nativeGetCommParamCapability(ip: String, port: Int, type: String? = null) =
        nativeCgi(ip, port, "getcommparamcapability.cgi", type?.let { "type=" + encode(it) })

    fun nativeGetCommParam(ip: String, port: Int, type: String) =
        nativeCgi(ip, port, "getcommparam.cgi", "type=" + encode(type))

    fun nativeGetCamParamCapability(ip: String, port: Int, type: String? = null) =
        nativeCgi(ip, port, "getcamparamcapability.cgi", type?.let { "type=" + encode(it) })

    fun nativeGetCamParam(ip: String, port: Int, type: String? = null) =
        nativeCgi(ip, port, "getcamparam.cgi", type?.let { "type=" + encode(it) })

    fun nativeCgiCandidates(ip: String, port: Int, name: String, query: String? = null): List<String> =
        listOf(
            nativeCgi(ip, port, name, query),
            nativeCgi(ip, port, "cgi-bin/$name", query),
        )

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
