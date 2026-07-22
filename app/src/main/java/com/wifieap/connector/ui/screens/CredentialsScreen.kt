package com.wifieap.connector.ui.screens

import android.net.Uri
import android.net.wifi.WifiEnterpriseConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.wifieap.connector.CertMode
import com.wifieap.connector.R
import com.wifieap.connector.ui.CredentialsFormState
import com.wifieap.connector.ui.theme.TvAccent
import com.wifieap.connector.ui.theme.TvBackground
import com.wifieap.connector.ui.theme.TvOnSurfaceDim
import com.wifieap.connector.ui.theme.TvSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CredentialsScreen(
    ssid: String,
    isManualSsid: Boolean,
    formState: CredentialsFormState,
    selectedCertName: String?,
    selectedCertUri: Uri?,
    onPickCertificate: () -> Unit,
    onConnect: (ssid: String, username: String, password: String, domain: String, eapMethod: Int, phase2Method: Int, certUri: Uri?, certMode: Int) -> Unit,
    onBack: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var ssidError by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    val eapMethods = listOf("PEAP", "TLS", "TTLS")
    val phase2Methods = listOf("MSCHAPV2", "GTC", "PAP", "CHAP")

    val ssidFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

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
                value = formState.editSsid,
                onValueChange = { formState.editSsid = it; ssidError = false },
                label = stringResource(R.string.label_ssid),
                imeAction = ImeAction.Next,
                isError = ssidError,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .focusRequester(ssidFocusRequester)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        TvTextField(
            value = formState.username,
            onValueChange = { formState.username = it; usernameError = false },
            label = stringResource(R.string.label_username),
            imeAction = ImeAction.Next,
            isError = usernameError,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .focusRequester(usernameFocusRequester)
        )
        Spacer(modifier = Modifier.height(16.dp))

        TvTextField(
            value = formState.password,
            onValueChange = { formState.password = it; passwordError = false },
            label = stringResource(R.string.label_password),
            isPassword = true,
            imeAction = ImeAction.Done,
            isError = passwordError,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .focusRequester(passwordFocusRequester)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            onClick = { showAdvanced = !showAdvanced },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvSurface,
                focusedContainerColor = TvAccent.copy(alpha = 0.3f)
            ),
            modifier = Modifier.widthIn(max = 600.dp).height(48.dp)
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
                selectedIndex = formState.eapIndex,
                onSelect = { formState.eapIndex = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.label_phase2), fontSize = 16.sp, color = TvOnSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(
                options = phase2Methods,
                selectedIndex = formState.phase2Index,
                onSelect = { formState.phase2Index = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            TvTextField(
                value = formState.domain,
                onValueChange = { formState.domain = it },
                label = stringResource(R.string.label_domain),
                modifier = Modifier.widthIn(max = 600.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.label_ca_cert), fontSize = 16.sp, color = TvOnSurfaceDim)
            Spacer(modifier = Modifier.height(8.dp))
            val certModeOptions = remember {
                buildList {
                    add(CertMode.SYSTEM)
                    add(CertMode.CUSTOM)
                    // Trust On First Use 仅在 Android 12 (API 31)+ 系统上受支持
                    if (android.os.Build.VERSION.SDK_INT >= 31) add(CertMode.NONE)
                }
            }
            val certModeLabels = listOf(
                stringResource(R.string.cert_system),
                stringResource(R.string.cert_select_file),
                stringResource(R.string.cert_none)
            )
            OptionRow(
                options = certModeOptions.map { certModeLabels[it] },
                selectedIndex = certModeOptions.indexOf(formState.certMode).coerceAtLeast(0),
                onSelect = { formState.certMode = certModeOptions[it] }
            )

            if (formState.certMode == CertMode.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = onPickCertificate,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvSurface,
                        focusedContainerColor = TvAccent.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.widthIn(max = 600.dp).height(48.dp)
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
                    val finalSsid = if (isManualSsid) formState.editSsid else ssid
                    if (isManualSsid && finalSsid.isBlank()) {
                        ssidError = true
                        ssidFocusRequester.requestFocus()
                        return@Surface
                    }
                    if (formState.username.isBlank()) {
                        usernameError = true
                        usernameFocusRequester.requestFocus()
                        return@Surface
                    }
                    if (formState.password.isBlank()) {
                        passwordError = true
                        passwordFocusRequester.requestFocus()
                        return@Surface
                    }
                    val eapMethod = when (formState.eapIndex) {
                        0 -> WifiEnterpriseConfig.Eap.PEAP
                        1 -> WifiEnterpriseConfig.Eap.TLS
                        2 -> WifiEnterpriseConfig.Eap.TTLS
                        else -> WifiEnterpriseConfig.Eap.PEAP
                    }
                    val phase2Method = when (formState.phase2Index) {
                        0 -> WifiEnterpriseConfig.Phase2.MSCHAPV2
                        1 -> WifiEnterpriseConfig.Phase2.GTC
                        2 -> WifiEnterpriseConfig.Phase2.PAP
                        3 -> WifiEnterpriseConfig.Phase2.MSCHAPV2
                        else -> WifiEnterpriseConfig.Phase2.GTC
                    }
                    onConnect(finalSsid, formState.username, formState.password, formState.domain, eapMethod, phase2Method, selectedCertUri, formState.certMode)
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
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 40.dp, vertical = 12.dp)
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
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 40.dp, vertical = 12.dp)
                ) {
                    Text(text = stringResource(R.string.btn_back), fontSize = 22.sp, color = Color.White)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            if (isManualSsid) ssidFocusRequester.requestFocus()
            else usernameFocusRequester.requestFocus()
        } catch (_: Exception) {}
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
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 20.dp, vertical = 8.dp)
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
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    val fieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isFieldFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = if (isError) Color(0xFFF44336) else TvOnSurfaceDim,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            onClick = {
                fieldFocusRequester.requestFocus()
                keyboardController?.show()
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isError) Color(0xFFF44336).copy(alpha = 0.1f)
                    else if (isFieldFocused) TvAccent.copy(alpha = 0.15f) else TvSurface,
                focusedContainerColor = if (isError) Color(0xFFF44336).copy(alpha = 0.15f)
                    else if (isFieldFocused) TvAccent.copy(alpha = 0.15f) else TvAccent.copy(alpha = 0.3f)
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Ascii,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            if (onImeAction != null) onImeAction()
                            else focusManager.moveFocus(FocusDirection.Down)
                        },
                        onDone = {
                            if (onImeAction != null) onImeAction()
                            else keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocusRequester)
                        .onFocusChanged { focusState ->
                            isFieldFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                keyboardController?.show()
                            }
                        }
                )
                if (value.isEmpty() && !isError) {
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
