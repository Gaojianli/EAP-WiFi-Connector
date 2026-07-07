@file:Suppress("DEPRECATION")

package com.wifieap.connector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiManager
import android.util.Log
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class WifiNetwork(
    val ssid: String,
    val level: Int,
    val capabilities: String
)

sealed class ConnectResult {
    object Success : ConnectResult()
    data class Failure(val reason: String) : ConnectResult()
}

class WiFiEapConnector(private val context: Context) {
    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scanReceiver: BroadcastReceiver? = null

    fun ensureWifiEnabled(): Boolean {
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
            Thread.sleep(2000)
        }
        return wifiManager.isWifiEnabled
    }

    fun startScan(onResults: (List<WifiNetwork>) -> Unit) {
        scanReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val results = getEnterpriseNetworks()
                onResults(results)
            }
        }

        context.registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        wifiManager.startScan()
    }

    fun stopScan() {
        scanReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        scanReceiver = null
    }

    fun getEnterpriseNetworks(): List<WifiNetwork> {
        val results = wifiManager.scanResults ?: return emptyList()
        return results
            .filter { it.capabilities.contains("EAP") || it.capabilities.contains("802.1x", ignoreCase = true) }
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, scans) -> scans.maxByOrNull { it.level }!! }
            .sortedByDescending { it.level }
            .map { WifiNetwork(ssid = it.SSID, level = it.level, capabilities = it.capabilities) }
    }

    fun connectToWiFi(
        ssid: String,
        username: String,
        password: String,
        domain: String = "",
        eapMethod: Int = WifiEnterpriseConfig.Eap.PEAP,
        phase2Method: Int = WifiEnterpriseConfig.Phase2.GTC,
        certificateUri: Uri? = null,
        useSystemCert: Boolean = true
    ): ConnectResult {
        try {
            Log.i(TAG, "Connecting to SSID: $ssid with user: $username")
            ensureWifiEnabled()

            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""

                val eapConfig = WifiEnterpriseConfig().apply {
                    this.eapMethod = eapMethod
                    this.phase2Method = phase2Method
                    identity = username
                    this.password = password

                    if (domain.isNotEmpty()) {
                        subjectMatch = domain
                    }

                    if (!useSystemCert && certificateUri != null) {
                        val caCert = loadCertificateFromUri(certificateUri)
                        if (caCert != null) {
                            caCertificate = caCert
                        }
                    }
                }

                enterpriseConfig = eapConfig
                allowedKeyManagement.clear()
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
            }

            wifiManager.configuredNetworks?.forEach { config ->
                if (config.SSID == "\"$ssid\"") {
                    wifiManager.removeNetwork(config.networkId)
                }
            }

            val networkId = wifiManager.addNetwork(wifiConfig)

            return if (networkId != -1) {
                Log.i(TAG, "WiFi configuration added with ID: $networkId")
                wifiManager.saveConfiguration()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                ConnectResult.Success
            } else {
                Log.e(TAG, "Failed to add WiFi configuration")
                ConnectResult.Failure(context.getString(R.string.error_add_network_failed))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WiFi: ${e.message}")
            e.printStackTrace()
            return ConnectResult.Failure(context.getString(R.string.error_exception, e.message ?: e.toString()))
        }
    }

    private fun loadCertificateFromUri(uri: Uri): X509Certificate? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return null
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(inputStream)
            inputStream.close()
            cert as X509Certificate
        } catch (e: Exception) {
            Log.e(TAG, "Error loading certificate from URI: ${e.message}")
            null
        }
    }

    fun disconnectWiFi() {
        try {
            wifiManager.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WiFi: " + e.message)
        }
    }

    companion object {
        private const val TAG = "WiFiEapConnector"
    }
}
