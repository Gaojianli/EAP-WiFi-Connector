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
        certificateUri: Uri? = null
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
                    
                    // 加载CA证书
                    val caCert = if (certificateUri != null) {
                        loadCertificateFromUri(certificateUri)
                    } else {
                        loadBuiltinCertificate()
                    }
                    
                    if (caCert != null) {
                        caCertificate = caCert
                        Log.d(TAG, "CA certificate loaded successfully")
                    } else {
                        Log.w(TAG, "No CA certificate loaded")
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