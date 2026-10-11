package com.longhorn.dvr.worldgm

import org.junit.Assert.*
import org.junit.Test

class SigmaReadbackTest {
    @Test fun rejectsFirmwareCommandEchoAndHomePage() {
        assertNull(SigmaReadback.parse("Videores=nvconf get 0 Camera.Menu.VideoRes", "Videores"))
        assertNull(SigmaReadback.parse("<Value>nvconf get 0 Camera.Menu.HDR</Value>", "HDR"))
        assertNull(SigmaReadback.parse("<!DOCTYPE html><html><body>Congratulations!</body></html>", "HDR"))
        assertNull(SigmaReadback.parse("Usage CGI_PROCESS.sh {no this parameter}", "HDR"))
        assertNull(SigmaReadback.parse("OK", "HDR"))
        assertNull(SigmaReadback.parse("SUCCESS", "HDR"))
    }
    @Test fun acceptsOnlyUnambiguousValues() {
        assertEquals("1440P30fps", SigmaReadback.parse("Videores=1440P30fps", "Videores"))
        assertEquals("ON", SigmaReadback.parse("<Response><Value>ON</Value></Response>", "HDR"))
        assertEquals("My DVR", SigmaReadback.parse("Net.WIFI_AP.SSID=My DVR", "Net.WIFI_AP.SSID"))
        assertNull(SigmaReadback.parse("<Value>ON</Value><Value>OFF</Value>", "HDR"))
        assertNull(SigmaReadback.parse("WrongField=ON", "HDR"))
        assertNull(SigmaReadback.parse("HTTP 200", "HDR"))
        assertNull(SigmaReadback.parse("<Error>failed</Error><Value>ON</Value>", "HDR"))
    }
    @Test fun doesNotGuessInverseBooleanOrUnknownEnums() {
        assertFalse(SigmaReadback.matches("0", "ON"))
        assertFalse(SigmaReadback.matches("1", "ON"))
        assertFalse(SigmaReadback.matches(null, "OFF"))
        assertFalse(SigmaReadback.matches("UNRECOGNIZED", "FINE"))
        assertTrue(SigmaReadback.matches(" on ", "ON"))
    }
    @Test fun usesFirmwareGetterNamesAndNeverReadsPasswords() {
        assertEquals("Videores", SigmaReadback.getters["VideoRes"])
        assertEquals("VideoClipTime", SigmaReadback.getters["LoopingVideo"])
        assertFalse(SigmaReadback.getters.keys.any { it.contains("CryptoKey") })
        assertFalse(SigmaReadback.getters.containsKey("setbitrate"))
    }
}
