package com.wifieap.connector

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.wifieap.connector.ui.ConnectState
import com.wifieap.connector.ui.WifiEapApp

class MainActivity : ComponentActivity() {
    private lateinit var wifiConnector: WiFiEapConnector

    private val networks = mutableStateListOf<WifiNetwork>()
    private var isScanning by mutableStateOf(false)
    private var connectState by mutableStateOf<ConnectState?>(null)
    private var connectError by mutableStateOf<String?>(null)
    private var isSuggestionMode by mutableStateOf(false)
    private var selectedCertUri by mutableStateOf<Uri?>(null)
    private var selectedCertName by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startWifiScan()
        }
    }

    private val certPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedCertUri = it
            selectedCertName = getFileName(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiConnector = WiFiEapConnector(this)

        setContent {
            WifiEapApp(
                networks = networks,
                isScanning = isScanning,
                onRefresh = { startWifiScan() },
                onConnect = { ssid, username, password, domain, eapMethod, phase2Method, certUri, certMode ->
                    doConnect(ssid, username, password, domain, eapMethod, phase2Method, certUri, certMode)
                },
                onPickCertificate = {
                    try {
                        certPickerLauncher.launch(arrayOf(
                            "application/x-pem-file",
                            "application/x-x509-ca-cert",
                            "application/pkcs8",
                            "application/x-pkcs12",
                            "*/*"
                        ))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this,
                            getString(R.string.no_file_picker),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                connectState = connectState,
                connectError = connectError,
                isSuggestionMode = isSuggestionMode,
                selectedCertUri = selectedCertUri,
                selectedCertName = selectedCertName
            )
        }

        requestPermissionsAndScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiConnector.stopScan()
    }

    private fun requestPermissionsAndScan() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startWifiScan()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun startWifiScan() {
        isScanning = true
        wifiConnector.ensureWifiEnabled()
        val started = wifiConnector.startScan { results ->
            runOnUiThread {
                networks.clear()
                networks.addAll(results)
                isScanning = false
            }
        }
        if (!started) {
            isScanning = false
            android.widget.Toast.makeText(this, getString(R.string.permission_required), android.widget.Toast.LENGTH_SHORT).show()
            requestPermissionsAndScan()
        }
    }

    private fun doConnect(
        ssid: String,
        username: String,
        password: String,
        domain: String,
        eapMethod: Int,
        phase2Method: Int,
        certUri: Uri?,
        certMode: Int
    ) {
        connectState = ConnectState.Connecting
        connectError = null
        isSuggestionMode = android.os.Build.VERSION.SDK_INT >= 29
        Thread {
            val result = wifiConnector.connectToWiFi(
                ssid = ssid,
                username = username,
                password = password,
                domain = domain,
                eapMethod = eapMethod,
                phase2Method = phase2Method,
                certificateUri = certUri,
                certMode = certMode
            )
            runOnUiThread {
                when (result) {
                    is ConnectResult.Success -> {
                        connectState = ConnectState.Success
                        connectError = null
                        if (isSuggestionMode) {
                            android.widget.Toast.makeText(this@MainActivity, getString(R.string.suggestion_toast), android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    is ConnectResult.Failure -> {
                        connectState = ConnectState.Failed
                        connectError = result.reason
                    }
                }
            }
        }.start()
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                if (nameIndex != -1) it.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
