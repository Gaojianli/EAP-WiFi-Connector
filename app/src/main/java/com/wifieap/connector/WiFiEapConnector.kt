package com.wifieap.connector

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiManager
import android.util.Log
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class WiFiEapConnector(private val context: Context) {
    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun connectToWiFi(
        ssid: String,
        username: String,
        password: String,
        domain: String = "",
        eapMethod: Int = WifiEnterpriseConfig.Eap.PEAP,
        phase2Method: Int = WifiEnterpriseConfig.Phase2.GTC,
        certificateUri: Uri? = null,
        certType: Int = 0 // 0: 系统证书, 1: 内置证书, 2: 用户选择证书
    ): Boolean {
        try {
            Log.i(TAG, "Connecting to SSID: $ssid with user: $username")
            
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "Enabling WiFi...")
                wifiManager.setWifiEnabled(true)
                Thread.sleep(3000)
            }

            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                
                val eapConfig = WifiEnterpriseConfig().apply {
                    this.eapMethod = eapMethod
                    this.phase2Method = phase2Method
                    identity = username
                    this.password = password
                    
                    if (domain.isNotEmpty()) {
                        subjectMatch = domain
                        Log.d(TAG, "Set domain: $domain")
                    }
                    
                    // 根据证书类型加载CA证书
                    when (certType) {
                        0 -> { // 使用系统证书
                            // 不设置任何证书，让系统自己验证
                            Log.d(TAG, "Using system certificate validation")
                        }
                        1 -> { // 使用内置证书
                            val caCert = loadBuiltinCertificate()
                            if (caCert != null) {
                                caCertificate = caCert
                                Log.d(TAG, "Builtin CA certificate loaded successfully")
                            } else {
                                Log.w(TAG, "Failed to load builtin CA certificate")
                            }
                        }
                        2 -> { // 使用用户选择的证书
                            if (certificateUri != null) {
                                val caCert = loadCertificateFromUri(certificateUri)
                                if (caCert != null) {
                                    caCertificate = caCert
                                    Log.d(TAG, "User selected CA certificate loaded successfully")
                                } else {
                                    Log.w(TAG, "Failed to load user selected CA certificate")
                                }
                            } else {
                                Log.w(TAG, "Certificate URI is null for user selected certificate")
                            }
                        }
                    }
                }
                
                enterpriseConfig = eapConfig
                allowedKeyManagement.clear()
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
            }

            // 删除现有同名网络
            wifiManager.configuredNetworks?.forEach { config ->
                if (config.SSID == "\"$ssid\"") {
                    Log.d(TAG, "Removing existing configuration for $ssid")
                    wifiManager.removeNetwork(config.networkId)
                }
            }

            Log.d(TAG, "Adding network configuration...")
            val networkId = wifiManager.addNetwork(wifiConfig)
            
            if (networkId != -1) {
                Log.i(TAG, "WiFi configuration added with ID: $networkId")
                wifiManager.saveConfiguration()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                return true
            } else {
                Log.e(TAG, "Failed to add WiFi configuration")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WiFi: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun loadBuiltinCertificate(): X509Certificate? {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.ca)
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(inputStream)
            inputStream.close()
            Log.d(TAG, "Builtin certificate loaded successfully")
            cert as X509Certificate
        } catch (e: Exception) {
            Log.e(TAG, "Error loading builtin CA certificate: ${e.message}")
            null
        }
    }

    private fun loadCertificateFromUri(uri: Uri): X509Certificate? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open input stream from URI")
            
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(inputStream)
            inputStream.close()
            
            Log.d(TAG, "Certificate loaded from URI: $uri")
            cert as X509Certificate
        } catch (e: Exception) {
            Log.e(TAG, "Error loading certificate from URI: ${e.message}")
            null
        }
    }

    fun disconnectWiFi() {
        try {
            wifiManager.disconnect()
            Log.i(TAG, "WiFi disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WiFi: " + e.message)
        }
    }

    companion object {
        private const val TAG = "WiFiEapConnector"
    }
}