package com.longhorn.dvr.worldgm

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

    private fun encodePath(remotePath: String): String =
        remotePath.replace('/', 36.toChar())

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
