package com.wifieap.connector.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wifieap.connector.WifiNetwork
import com.wifieap.connector.ui.screens.ConnectingScreen
import com.wifieap.connector.ui.screens.CredentialsScreen
import com.wifieap.connector.ui.screens.SelectNetworkScreen
import com.wifieap.connector.ui.theme.WifiEapTheme

enum class WizardStep { SelectNetwork, Credentials, Connecting }
enum class ConnectState { Connecting, Success, Failed }

/**
 * Hoisted form state for CredentialsScreen so that field values survive
 * navigation back and forth between Credentials and Connecting screens
 * (e.g. when the user hits Retry after a failed connection).
 */
class CredentialsFormState {
    var editSsid by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var domain by mutableStateOf("")
    var eapIndex by mutableIntStateOf(2)
    var phase2Index by mutableIntStateOf(2)
    var useSystemCert by mutableStateOf(true)

    fun resetFor(ssid: String) {
        editSsid = ssid
        username = ""
        password = ""
        domain = ""
        eapIndex = 2
        phase2Index = 2
        useSystemCert = true
    }
}

@Composable
fun WifiEapApp(
    networks: List<WifiNetwork>,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onConnect: (ssid: String, username: String, password: String, domain: String, eapMethod: Int, phase2Method: Int, certUri: Uri?, useSystemCert: Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onPickCertificate: () -> Unit,
    connectState: ConnectState?,
    connectError: String?,
    selectedCertUri: Uri?,
    selectedCertName: String?
) {
    WifiEapTheme {
        var step by remember { mutableStateOf(WizardStep.SelectNetwork) }
        var selectedSsid by remember { mutableStateOf("") }
        var isManualInput by remember { mutableStateOf(false) }
        val credentialsForm = remember { CredentialsFormState() }

        BackHandler(enabled = step != WizardStep.SelectNetwork) {
            when (step) {
                WizardStep.Credentials -> step = WizardStep.SelectNetwork
                WizardStep.Connecting -> step = WizardStep.Credentials
                else -> {}
            }
        }

        when (step) {
            WizardStep.SelectNetwork -> {
                SelectNetworkScreen(
                    networks = networks,
                    isScanning = isScanning,
                    onRefresh = onRefresh,
                    onNetworkSelected = { ssid ->
                        selectedSsid = ssid
                        isManualInput = false
                        credentialsForm.resetFor(ssid)
                        step = WizardStep.Credentials
                    },
                    onManualInput = {
                        selectedSsid = ""
                        isManualInput = true
                        credentialsForm.resetFor("")
                        step = WizardStep.Credentials
                    }
                )
            }
            WizardStep.Credentials -> {
                CredentialsScreen(
                    ssid = selectedSsid,
                    isManualSsid = isManualInput,
                    formState = credentialsForm,
                    selectedCertName = selectedCertName,
                    onPickCertificate = onPickCertificate,
                    onConnect = { ssid, username, password, domain, eapMethod, phase2Method, certUri, useSystemCert ->
                        selectedSsid = ssid
                        step = WizardStep.Connecting
                        onConnect(ssid, username, password, domain, eapMethod, phase2Method, certUri, useSystemCert)
                    },
                    onBack = { step = WizardStep.SelectNetwork },
                    selectedCertUri = selectedCertUri
                )
            }
            WizardStep.Connecting -> {
                ConnectingScreen(
                    ssid = selectedSsid,
                    state = connectState,
                    errorMessage = connectError,
                    onRetry = { step = WizardStep.Credentials },
                    onDone = { step = WizardStep.SelectNetwork },
                    onDisconnect = onDisconnect
                )
            }
        }
    }
}
