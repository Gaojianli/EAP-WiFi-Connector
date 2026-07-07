package com.wifieap.connector.ui.screens

import android.net.Uri
import android.net.wifi.WifiEnterpriseConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.wifieap.connector.R
import com.wifieap.connector.ui.theme.TvAccent
import com.wifieap.connector.ui.theme.TvBackground
import com.wifieap.connector.ui.theme.TvOnSurfaceDim
import com.wifieap.connector.ui.theme.TvSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CredentialsScreen(
    ssid: String,
    isManualSsid: Boolean,
    selectedCertName: String?,
    selectedCertUri: Uri?,
    onPickCertificate: () -> Unit,
    onConnect: (ssid: String, username: String, password: String, domain: String, eapMethod: Int, phase2Method: Int, certUri: Uri?, useSystemCert: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var editSsid by remember { mutableStateOf(ssid) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var domain by remember { mutableStateOf("") }
    var eapIndex by remember { mutableIntStateOf(2) }
    var phase2Index by remember { mutableIntStateOf(2) }
    var useSystemCert by remember { mutableStateOf(true) }

    val eapMethods = listOf("PEAP", "TLS", "TTLS")
    val phase2Methods = listOf("MSCHAPV2", "GTC", "PAP", "CHAP")

    val focusRequester = remember { FocusRequester() }

    val titleText = if (isManualSsid) {
        stringResource(R.string.title_manual_input)
    } else {
        stringResource(R.string.title_connect_to, ssid)
    }
    val advancedLabel = stringResource(R.string.advanced_settings)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(48.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = titleText,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isManualSsid) {
            TvTextField(
                value = editSsid,
                onValueChange = { editSsid = it },
                label = stringResource(R.string.label_ssid),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        TvTextField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.label_username),
            modifier = if (!isManualSsid) {
                Modifier.fillMaxWidth().focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        TvTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.label_password),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            onClick = { showAdvanced = !showAdvanced },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvSurface,
                focusedContainerColor = TvAccent.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                Text(
                    text = if (showAdvanced) "▼ $advancedLabel" else "▶ $advancedLabel",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }

        if (showAdvanced) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.label_eap_method), fontSize = 16.sp, color = TvOnSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(
                options = eapMethods,
                selectedIndex = eapIndex,
                onSelect = { eapIndex = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.label_phase2), fontSize = 16.sp, color = TvOnSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(
                options = phase2Methods,
                selectedIndex = phase2Index,
                onSelect = { phase2Index = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            TvTextField(
                value = domain,
                onValueChange = { domain = it },
                label = stringResource(R.string.label_domain),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.label_ca_cert), fontSize = 16.sp, color = TvOnSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(
                options = listOf(stringResource(R.string.cert_system), stringResource(R.string.cert_select_file)),
                selectedIndex = if (useSystemCert) 0 else 1,
                onSelect = { useSystemCert = it == 0 }
            )

            if (!useSystemCert) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = onPickCertificate,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvSurface,
                        focusedContainerColor = TvAccent.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = selectedCertName ?: stringResource(R.string.cert_pick_prompt),
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                onClick = {
                    val finalSsid = if (isManualSsid) editSsid else ssid
                    if (finalSsid.isBlank() || username.isBlank() || password.isBlank()) return@Surface
                    val eapMethod = when (eapIndex) {
                        0 -> WifiEnterpriseConfig.Eap.PEAP
                        1 -> WifiEnterpriseConfig.Eap.TLS
                        2 -> WifiEnterpriseConfig.Eap.TTLS
                        else -> WifiEnterpriseConfig.Eap.PEAP
                    }
                    val phase2Method = when (phase2Index) {
                        0 -> WifiEnterpriseConfig.Phase2.MSCHAPV2
                        1 -> WifiEnterpriseConfig.Phase2.GTC
                        2 -> WifiEnterpriseConfig.Phase2.PAP
                        3 -> WifiEnterpriseConfig.Phase2.MSCHAPV2
                        else -> WifiEnterpriseConfig.Phase2.GTC
                    }
                    onConnect(finalSsid, username, password, domain, eapMethod, phase2Method, selectedCertUri, useSystemCert)
                },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvAccent,
                    focusedContainerColor = TvAccent.copy(alpha = 0.8f)
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp)
                ) {
                    Text(text = stringResource(R.string.btn_connect), fontSize = 22.sp, color = Color.White)
                }
            }

            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvSurface,
                    focusedContainerColor = TvAccent.copy(alpha = 0.3f)
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp)
                ) {
                    Text(text = stringResource(R.string.btn_back), fontSize = 22.sp, color = Color.White)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) TvAccent else TvSurface,
                    focusedContainerColor = TvAccent.copy(alpha = if (isSelected) 1f else 0.5f)
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = option,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = TvOnSurfaceDim,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvSurface,
                focusedContainerColor = TvSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 20.sp,
                        color = Color.White
                    ),
                    singleLine = true,
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        fontSize = 20.sp,
                        color = TvOnSurfaceDim.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
