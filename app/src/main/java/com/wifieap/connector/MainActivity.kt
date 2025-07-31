package com.wifieap.connector

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiEnterpriseConfig
import android.os.Bundle
import android.security.KeyChain
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var wifiConnector: WiFiEapConnector
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var btnSelectCert: Button
    private lateinit var btnInstallCert: Button
    private lateinit var statusText: TextView
    private lateinit var tvSelectedCert: TextView
    
    // 输入控件
    private lateinit var etSsid: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etDomain: TextInputEditText
    private lateinit var spinnerEapMethod: Spinner
    private lateinit var spinnerPhase2: Spinner
    
    private var selectedCertificateUri: Uri? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val CERTIFICATE_PICK_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupSpinners()
        setupClickListeners()
        
        wifiConnector = WiFiEapConnector(this)
        updateStatus("准备连接WiFi")
    }

    private fun initViews() {
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        btnSelectCert = findViewById(R.id.btnSelectCert)
        btnInstallCert = findViewById(R.id.btnInstallCert)
        statusText = findViewById(R.id.statusText)
        tvSelectedCert = findViewById(R.id.tvSelectedCert)
        
        etSsid = findViewById(R.id.etSsid)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etDomain = findViewById(R.id.etDomain)
        spinnerEapMethod = findViewById(R.id.spinnerEapMethod)
        spinnerPhase2 = findViewById(R.id.spinnerPhase2)
    }

    private fun setupSpinners() {
        // EAP方法下拉框
        val eapAdapter = ArrayAdapter.createFromResource(
            this, R.array.eap_methods, android.R.layout.simple_spinner_item
        )
        eapAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEapMethod.adapter = eapAdapter
        spinnerEapMethod.setSelection(0) // 默认选择PEAP

        // Phase2方法下拉框
        val phase2Adapter = ArrayAdapter.createFromResource(
            this, R.array.phase2_methods, android.R.layout.simple_spinner_item
        )
        phase2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPhase2.adapter = phase2Adapter
        spinnerPhase2.setSelection(1) // 默认选择GTC
    }

    private fun setupClickListeners() {
        connectButton.setOnClickListener {
            if (checkPermissions()) {
                connectToWiFi()
            } else {
                requestPermissions()
            }
        }

        disconnectButton.setOnClickListener {
            disconnectWiFi()
        }

        btnSelectCert.setOnClickListener {
            selectCertificate()
        }

        btnInstallCert.setOnClickListener {
            installCertificateToSystem()
        }
    }

    private fun selectCertificate() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/x-pem-file",
                "application/x-x509-ca-cert",
                "application/pkcs8",
                "application/x-pkcs12"
            ))
        }
        try {
            startActivityForResult(intent, CERTIFICATE_PICK_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CERTIFICATE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedCertificateUri = uri
                val fileName = getFileName(uri) ?: "选择的证书"
                tvSelectedCert.text = "已选择: $fileName"
                Toast.makeText(this, "证书已选择", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                if (nameIndex != -1) it.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                connectToWiFi()
            } else {
                Toast.makeText(this, "需要权限才能连接WiFi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToWiFi() {
        val ssid = etSsid.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val domain = etDomain.text.toString().trim()

        if (ssid.isEmpty()) {
            Toast.makeText(this, "请输入WiFi网络名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isEmpty()) {
            Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取选择的加密方式
        val eapMethod = when (spinnerEapMethod.selectedItem.toString()) {
            "PEAP" -> WifiEnterpriseConfig.Eap.PEAP
            "TLS" -> WifiEnterpriseConfig.Eap.TLS
            "TTLS" -> WifiEnterpriseConfig.Eap.TTLS
            "AKA" -> WifiEnterpriseConfig.Eap.AKA
            else -> WifiEnterpriseConfig.Eap.PEAP
        }

        val phase2Method = when (spinnerPhase2.selectedItem.toString()) {
            "MSCHAPV2" -> WifiEnterpriseConfig.Phase2.MSCHAPV2
            "GTC" -> WifiEnterpriseConfig.Phase2.GTC
            "PAP" -> WifiEnterpriseConfig.Phase2.PAP
            else -> WifiEnterpriseConfig.Phase2.GTC
        }

        updateStatus("正在连接 $ssid...")
        connectButton.isEnabled = false

        Thread {
            val success = wifiConnector.connectToWiFi(
                ssid = ssid,
                username = username,
                password = password,
                domain = domain,
                eapMethod = eapMethod,
                phase2Method = phase2Method,
                certificateUri = selectedCertificateUri
            )

            runOnUiThread {
                connectButton.isEnabled = true
                if (success) {
                    updateStatus("已连接到 $ssid")
                    Toast.makeText(this@MainActivity, "WiFi连接成功", Toast.LENGTH_SHORT).show()
                } else {
                    updateStatus("连接失败")
                    Toast.makeText(this@MainActivity, "WiFi连接失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun disconnectWiFi() {
        wifiConnector.disconnectWiFi()
        updateStatus("已断开连接")
        Toast.makeText(this, "WiFi已断开", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus(status: String) {
        statusText.text = "状态: $status"
    }

    private fun installCertificateToSystem() {
        try {
            val rawCertificateData = if (selectedCertificateUri != null) {
                // 使用选择的证书
                getCertificateBytes(selectedCertificateUri!!)
            } else {
                // 使用内置证书
                getBuiltinCertificateBytes()
            }

            if (rawCertificateData != null) {
                // 处理证书数据，确保格式正确
                val processedCertData = processCertificateData(rawCertificateData)
                
                if (processedCertData != null) {
                    android.util.Log.d("CertInstall", "Processed certificate size: ${processedCertData.size}")
                    
                    // 尝试多种安装方法
                    if (!tryInstallWifiCertificate(processedCertData) && 
                        !trySystemCertificateInstaller(processedCertData) &&
                        !fallbackInstallCertificate(processedCertData)) {
                        
                        Toast.makeText(this, "所有证书安装方法都失败了", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "证书格式处理失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "无法读取证书数据", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CertInstall", "Certificate installation error", e)
            Toast.makeText(this, "证书安装失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun processCertificateData(rawData: ByteArray): ByteArray? {
        return try {
            val certString = String(rawData)
            
            // 如果是PEM格式，直接返回
            if (certString.contains("-----BEGIN CERTIFICATE-----") && 
                certString.contains("-----END CERTIFICATE-----")) {
                android.util.Log.d("CertInstall", "Using PEM format certificate")
                return rawData
            }
            
            // 如果是DER格式，也直接返回
            if (rawData[0] == 0x30.toByte()) {
                android.util.Log.d("CertInstall", "Using DER format certificate")
                return rawData
            }
            
            android.util.Log.e("CertInstall", "Unknown certificate format")
            null
        } catch (e: Exception) {
            android.util.Log.e("CertInstall", "Error processing certificate data", e)
            null
        }
    }

    private fun tryInstallWifiCertificate(certificateData: ByteArray): Boolean {
        return try {
            // 方法1：使用标准的证书安装Intent
            val intent = Intent("android.credentials.INSTALL").apply {
                putExtra("certificate", certificateData)
                putExtra("name", "WiFi_CA_Certificate")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // 检查是否有应用可以处理此Intent
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Toast.makeText(this, "正在安装WiFi证书...\n请在安装界面选择'用于WiFi'", Toast.LENGTH_LONG).show()
                true
            } else {
                android.util.Log.d("CertInstall", "No app found to handle certificate install intent")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CertInstall", "WiFi certificate install failed", e)
            false
        }
    }

    private fun trySystemCertificateInstaller(certificateData: ByteArray): Boolean {
        return try {
            // 方法2：尝试其他可能的Intent
            val intent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(android.net.Uri.parse("content://certificate"), "application/x-x509-ca-cert")
                putExtra("certificate", certificateData)
                putExtra("name", "WiFi_CA_Certificate")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Toast.makeText(this, "正在通过系统安装器安装证书...", Toast.LENGTH_LONG).show()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CertInstall", "System certificate installer failed", e)
            false
        }
    }

    private fun fallbackInstallCertificate(certificateData: ByteArray): Boolean {
        return try {
            val intent = KeyChain.createInstallIntent().apply {
                putExtra(KeyChain.EXTRA_CERTIFICATE, certificateData)
                putExtra(KeyChain.EXTRA_NAME, "WiFi_CA_Certificate")
            }
            startActivity(intent)
            Toast.makeText(this, "正在安装证书...\n安装时请选择'用于WiFi'\n然后在WiFi设置中选择此证书", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "所有证书安装方法均失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun getCertificateBytes(uri: Uri): ByteArray? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
    }

    private fun getBuiltinCertificateBytes(): ByteArray? {
        return try {
            val inputStream = resources.openRawResource(R.raw.ca)
            val bytes = inputStream.use { it.readBytes() }
            
            // 诊断信息
            android.util.Log.d("CertInstall", "Certificate size: ${bytes.size} bytes")
            android.util.Log.d("CertInstall", "Certificate starts with: ${String(bytes.take(50).toByteArray())}")
            
            // 验证证书格式
            if (bytes.isEmpty()) {
                Toast.makeText(this, "证书文件为空", Toast.LENGTH_SHORT).show()
                return null
            }
            
            // 检查是否为PEM格式
            val certString = String(bytes)
            if (!certString.contains("-----BEGIN CERTIFICATE-----")) {
                Toast.makeText(this, "证书格式不是PEM格式", Toast.LENGTH_SHORT).show()
                return null
            }
            
            bytes
        } catch (e: IOException) {
            android.util.Log.e("CertInstall", "Error reading builtin certificate", e)
            Toast.makeText(this, "读取内置证书失败: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}