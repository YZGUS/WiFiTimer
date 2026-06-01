package com.cengyi.wifitimer.util

import android.content.Context
import android.net.wifi.WifiManager

object WifiScanner {

    data class ScanResult(
        val ssid: String,
        val bssid: String,
        val level: Int
    )

    fun startScan(context: Context) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        runCatching { wifiManager.startScan() }
    }

    fun getAvailableNetworks(context: Context): List<ScanResult> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val results = mutableListOf<ScanResult>()

        // Currently connected WiFi
        val connectionInfo = wifiManager.connectionInfo
        val connectedSsid = connectionInfo?.ssid?.trim('"')
        if (!connectedSsid.isNullOrBlank() && !isUnknownSsid(connectedSsid)) {
            results.add(
                ScanResult(
                    ssid = connectedSsid,
                    bssid = connectionInfo.bssid ?: "",
                    level = connectionInfo.rssi
                )
            )
        }

        // Scan results (requires location permission granted at runtime)
        val scanResults = wifiManager.scanResults ?: emptyList()
        for (scan in scanResults) {
            val ssid = scan.SSID?.trim('"') ?: continue
            if (ssid.isBlank() || isUnknownSsid(ssid)) continue
            if (results.none { it.ssid == ssid }) {
                results.add(
                    ScanResult(
                        ssid = ssid,
                        bssid = scan.BSSID ?: "",
                        level = scan.level
                    )
                )
            }
        }

        return results.sortedByDescending { it.level }
    }

    private fun isUnknownSsid(ssid: String): Boolean {
        return ssid.equals("<unknown ssid>", ignoreCase = true) ||
                ssid == "0x" ||
                ssid == "unknown ssid"
    }
}
