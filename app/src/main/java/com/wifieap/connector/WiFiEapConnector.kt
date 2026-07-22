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
import androidx.core.content.ContextCompat
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

/**
 * CA 证书验证方式：
 * - SYSTEM: 使用系统证书库验证服务器证书
 * - CUSTOM: 使用用户选择的 CA 证书文件验证
 * - NONE: 不验证证书，仅依赖 Trust On First Use（TOFU，安全性较低）
 */
object CertMode {
    const val SYSTEM = 0
    const val CUSTOM = 1
    const val NONE = 2
}

class WiFiEapConnector(private val context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scanReceiver: BroadcastReceiver? = null
    private val scanHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
        scanHandler.removeCallbacksAndMessages(null)

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                scanHandler.removeCallbacksAndMessages(null)
                onResults(getEnterpriseWiFi())
            }
        }

        context.registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        wifiManager.startScan()

        scanHandler.postDelayed({
            onResults(getEnterpriseWiFi())
        }, SCAN_TIMEOUT_MS)

        return true
    }

    fun getEnterpriseWiFi():List<WifiNetwork>{
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
            return emptyList()
        val results = wifiManager.scanResults ?: emptyList()
        return results
            .asSequence()
            .filter { it.capabilities.contains("EAP") || it.capabilities.contains("802.1x", ignoreCase = true) }
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (_, scans) -> scans.maxByOrNull { it.level }!! }
            .sortedByDescending { it.level }
            .map { WifiNetwork(ssid = it.SSID, level = it.level, capabilities = it.capabilities) }
            .toList()
    }

    fun stopScan() {
        scanHandler.removeCallbacksAndMessages(null)
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
        certMode: Int = CertMode.SYSTEM
    ): ConnectResult {
        Log.i(TAG, "Connecting to SSID: $ssid, SDK: ${Build.VERSION.SDK_INT}")
        ensureWifiEnabled()

        return if (Build.VERSION.SDK_INT >= 29) {
            connectViaSuggestion(ssid, username, password, domain, eapMethod, phase2Method, certificateUri, certMode)
        } else {
            connectViaLegacy(ssid, username, password, domain, eapMethod, phase2Method, certificateUri, certMode)
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
        certMode: Int
    ): ConnectResult {
        // System / None(TOFU) 模式都依赖域名匹配（Domain Suffix Match）来完成"验证"，
        // 否则框架会认为该 EAP 配置"要求服务器证书但未启用验证"而拒绝
        if ((certMode == CertMode.SYSTEM || certMode == CertMode.NONE) && domain.isEmpty()) {
            return ConnectResult.Failure(context.getString(R.string.error_domain_required))
        }
        // Trust On First Use 仅在 Android 12 (API 31) 及以上系统才存在，
        // 更低版本选择"不验证证书"时无法满足框架的证书验证要求
        if (certMode == CertMode.NONE && Build.VERSION.SDK_INT < 31) {
            return ConnectResult.Failure(context.getString(R.string.error_tofu_unsupported))
        }

        try {
            val enterpriseConfig = WifiEnterpriseConfig().apply {
                this.eapMethod = eapMethod
                this.phase2Method = phase2Method
                identity = username
                this.password = password
                domainSuffixMatch = domain
                when (certMode) {
                    CertMode.CUSTOM -> {
                        if (certificateUri != null) {
                            val caCert = loadCertificateFromUri(certificateUri)
                            if (caCert != null) {
                                caCertificate = caCert
                                // 设置了 CA 根证书后必须显式关闭 Trust On First Use，
                                // 否则会报 "Trust On First Use could not be set when
                                // Root CA certificate is set" 并被拒绝
                                if (Build.VERSION.SDK_INT >= 31) {
                                    enableTrustOnFirstUse(false)
                                }
                            }
                        }
                    }
                    CertMode.SYSTEM -> {
                        // setCaPath is @hide but required to pass WifiNetworkSuggestion's internal validation
                        var caPathSet = false
                        try {
                            val method = WifiEnterpriseConfig::class.java.getMethod("setCaPath", String::class.java)
                            method.invoke(this, "/system/etc/security/cacerts")
                            caPathSet = true
                        } catch (_: Exception) {}
                        // 只有在 caPath 设置失败（即未配置任何 CA）时才回退到 TOFU，
                        // 否则同时设置 CA 路径与 TOFU 会导致 WifiConfigManager 拒绝该配置
                        if (!caPathSet && Build.VERSION.SDK_INT >= 31) {
                            enableTrustOnFirstUse(true)
                        }
                    }
                    CertMode.NONE -> {
                        // 不设置任何证书，仅依赖 Trust On First Use
                        if (Build.VERSION.SDK_INT >= 31) {
                            enableTrustOnFirstUse(true)
                        }
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
                val diagnostic = buildDiagnosticInfo(-1, eapMethod, phase2Method, certMode, certificateUri != null)
                ConnectResult.Failure("addNetworkSuggestions failed: status=$status\n$diagnostic")
            }
        } catch (e: Exception) {
            val diagnostic = buildDiagnosticInfo(-1, eapMethod, phase2Method, certMode, certificateUri != null)
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
        certMode: Int
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
                    if (certMode == CertMode.CUSTOM && certificateUri != null) {
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
                val diagnostic = buildDiagnosticInfo(networkId, eapMethod, phase2Method, certMode, certificateUri != null)
                ConnectResult.Failure("addNetwork failed\n$diagnostic")
            }
        } catch (e: Exception) {
            val diagnostic = buildDiagnosticInfo(-1, eapMethod, phase2Method, certMode, certificateUri != null)
            return ConnectResult.Failure("${e.javaClass.simpleName}: ${e.message}\n$diagnostic")
        }
    }

    private fun buildDiagnosticInfo(
        networkId: Int,
        eapMethod: Int,
        phase2Method: Int,
        certMode: Int,
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
            "certMode=$certMode, hasCert=$hasCert, " +
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
        WifiEnterpriseConfig.Phase2.MSCHAPV2 -> "MSCHAPv2"
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
        private const val SCAN_TIMEOUT_MS = 8000L
    }
}
