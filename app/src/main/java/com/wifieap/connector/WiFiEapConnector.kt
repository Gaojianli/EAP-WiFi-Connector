@file:Suppress("DEPRECATION")

package com.wifieap.connector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.collections.component1
import kotlin.collections.component2

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
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scanReceiver: BroadcastReceiver? = null

    fun ensureWifiEnabled(): Boolean {
        if (!wifiManager.isWifiEnabled && Build.VERSION.SDK_INT < 29) {
            wifiManager.isWifiEnabled = true
            Thread.sleep(2000)
        }
        return wifiManager.isWifiEnabled
    }

    fun startScan(onResults: (List<WifiNetwork>) -> Unit): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        scanReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    onResults(emptyList())
                    return
                }
                val results = wifiManager.scanResults ?: emptyList()
                onResults(results
                        .asSequence()
                        .filter { it.capabilities.contains("EAP") || it.capabilities.contains("802.1x", ignoreCase = true) }
                        .filter { it.SSID.isNotBlank() }
                        .groupBy { it.SSID }
                        .map { (_, scans) -> scans.maxByOrNull { it.level }!! }
                        .sortedByDescending { it.level }
                        .map { WifiNetwork(ssid = it.SSID, level = it.level, capabilities = it.capabilities) }
                        .toList())
            }
        }

        context.registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        wifiManager.startScan()
        return true
    }

    fun stopScan() {
        scanReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        scanReceiver = null
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
        Log.i(TAG, "Connecting to SSID: $ssid, SDK: ${Build.VERSION.SDK_INT}")
        ensureWifiEnabled()

        return if (Build.VERSION.SDK_INT >= 29) {
            connectViaSuggestion(ssid, username, password, domain, eapMethod, phase2Method, certificateUri, useSystemCert)
        } else {
            connectViaLegacy(ssid, username, password, domain, eapMethod, phase2Method, certificateUri, useSystemCert)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectViaSuggestion(
        ssid: String,
        username: String,
        password: String,
        domain: String,
        eapMethod: Int,
        phase2Method: Int,
        certificateUri: Uri?,
        useSystemCert: Boolean
    ): ConnectResult {
        if (useSystemCert && domain.isEmpty()) {
            return ConnectResult.Failure("Android 11+ requires Domain (Subject Match) when using system certificates")
        }

        try {
            val enterpriseConfig = WifiEnterpriseConfig().apply {
                this.eapMethod = eapMethod
                this.phase2Method = phase2Method
                identity = username
                this.password = password
                domainSuffixMatch = domain
                if (!useSystemCert && certificateUri != null) {
                    val caCert = loadCertificateFromUri(certificateUri)
                    if (caCert != null) {
                        caCertificate = caCert
                    }
                } else if (useSystemCert) {
                    try {
                        val method = WifiEnterpriseConfig::class.java.getMethod("setCaPath", String::class.java)
                        method.invoke(this, "/system/etc/security/cacerts")
                    } catch (_: Exception) {}
                    if (Build.VERSION.SDK_INT >= 33) {
                        enableTrustOnFirstUse(true)
                    }
                }
            }

            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2EnterpriseConfig(enterpriseConfig)
                .setIsAppInteractionRequired(true)
                .build()

            if (Build.VERSION.SDK_INT >= 30) {
                wifiManager.removeNetworkSuggestions(wifiManager.networkSuggestions)
            }

            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            return if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.i(TAG, "Network suggestion added successfully")
                ConnectResult.Success
            } else {
                val diagnostic = buildDiagnosticInfo(-1, eapMethod, phase2Method, useSystemCert, certificateUri != null)
                ConnectResult.Failure("addNetworkSuggestions failed: status=$status\n$diagnostic")
            }
        } catch (e: Exception) {
            val diagnostic = buildDiagnosticInfo(-1, eapMethod, phase2Method, useSystemCert, certificateUri != null)
            return ConnectResult.Failure("${e.javaClass.simpleName}: ${e.message}\n$diagnostic")
        }
    }

    private fun connectViaLegacy(
        ssid: String,
        username: String,
        password: String,
        domain: String,
        eapMethod: Int,
        phase2Method: Int,
        certificateUri: Uri?,
        useSystemCert: Boolean
    ): ConnectResult {
        try {
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                enterpriseConfig = WifiEnterpriseConfig().apply {
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
                allowedKeyManagement.clear()
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                wifiManager.configuredNetworks?.forEach { config ->
                    if (config.SSID == "\"$ssid\"") {
                        wifiManager.removeNetwork(config.networkId)
                    }
                }
            }

            val networkId = wifiManager.addNetwork(wifiConfig)
            return if (networkId != -1) {
                wifiManager.saveConfiguration()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                ConnectResult.Success
            } else {
                val diagnostic = buildDiagnosticInfo(networkId, eapMethod, phase2Method, useSystemCert, certificateUri != null)
                ConnectResult.Failure("addNetwork failed\n$diagnostic")
            }
        } catch (e: Exception) {
            val diagnostic = buildDiagnosticInfo(-1, eapMethod, phase2Method, useSystemCert, certificateUri != null)
            return ConnectResult.Failure("${e.javaClass.simpleName}: ${e.message}\n$diagnostic")
        }
    }

    private fun buildDiagnosticInfo(
        networkId: Int,
        eapMethod: Int,
        phase2Method: Int,
        useSystemCert: Boolean,
        hasCert: Boolean
    ): String {
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasWifi = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CHANGE_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return "addNetwork=$networkId, sdk=${Build.VERSION.SDK_INT}, " +
            "device=${Build.MANUFACTURER} ${Build.MODEL}, " +
            "wifiEnabled=${wifiManager.isWifiEnabled}, " +
            "eap=${eapMethodName(eapMethod)}, phase2=${phase2MethodName(phase2Method)}, " +
            "useSystemCert=$useSystemCert, hasCert=$hasCert, " +
            "location=$hasLocation, wifi=$hasWifi"
    }

    private fun eapMethodName(method: Int): String = when (method) {
        WifiEnterpriseConfig.Eap.PEAP -> "PEAP"
        WifiEnterpriseConfig.Eap.TLS -> "TLS"
        WifiEnterpriseConfig.Eap.TTLS -> "TTLS"
        WifiEnterpriseConfig.Eap.PWD -> "PWD"
        WifiEnterpriseConfig.Eap.SIM -> "SIM"
        WifiEnterpriseConfig.Eap.AKA -> "AKA"
        else -> "UNKNOWN($method)"
    }

    private fun phase2MethodName(method: Int): String = when (method) {
        WifiEnterpriseConfig.Phase2.NONE -> "NONE"
        WifiEnterpriseConfig.Phase2.PAP -> "PAP"
        WifiEnterpriseConfig.Phase2.MSCHAP -> "MSCHAP"
        WifiEnterpriseConfig.Phase2.MSCHAPV2 -> "MSCHAPV2"
        WifiEnterpriseConfig.Phase2.GTC -> "GTC"
        else -> "UNKNOWN($method)"
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
            Log.e(TAG, "Error loading certificate: ${e.message}")
            null
        }
    }

    fun disconnectWiFi() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                wifiManager.removeNetworkSuggestions(wifiManager.networkSuggestions)
            } else if (Build.VERSION.SDK_INT >= 29) {
                wifiManager.removeNetworkSuggestions(emptyList())
            } else {
                wifiManager.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WiFiEapConnector"
    }
}
