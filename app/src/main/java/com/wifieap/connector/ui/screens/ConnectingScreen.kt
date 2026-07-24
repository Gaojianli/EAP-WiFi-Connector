package com.wifieap.connector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.wifieap.connector.R
import com.wifieap.connector.ui.ConnectState
import com.wifieap.connector.ui.theme.TvAccent
import com.wifieap.connector.ui.theme.TvBackground
import com.wifieap.connector.ui.theme.TvError
import com.wifieap.connector.ui.theme.TvOnSurfaceDim
import com.wifieap.connector.ui.theme.TvSuccess
import com.wifieap.connector.ui.theme.WifiEapTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConnectingScreen(
    ssid: String,
    state: ConnectState?,
    errorMessage: String? = null,
    isSuggestionMode: Boolean = false,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = ssid,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        when (state) {
            ConnectState.Connecting, null -> {
                Text(
                    text = stringResource(R.string.connecting),
                    fontSize = 24.sp,
                    color = TvOnSurfaceDim
                )
            }
            ConnectState.Success -> {
                Text(
                    text = if (isSuggestionMode) stringResource(R.string.suggestion_added)
                           else stringResource(R.string.connect_success),
                    fontSize = 32.sp,
                    color = TvSuccess,
                    modifier = Modifier.padding(bottom = if (isSuggestionMode) 8.dp else 24.dp)
                )

                if (isSuggestionMode) {
                    Text(
                        text = stringResource(R.string.suggestion_added_hint),
                        fontSize = 16.sp,
                        color = TvOnSurfaceDim,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.colors(
                        containerColor = TvAccent,
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = TvAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text(text = stringResource(R.string.btn_done), fontSize = 22.sp)
                }
            }
            ConnectState.Failed -> {
                Text(
                    text = stringResource(R.string.connect_failed),
                    fontSize = 32.sp,
                    color = TvError,
                    modifier = Modifier.padding(bottom = if (errorMessage.isNullOrBlank()) 24.dp else 8.dp)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TvOnSurfaceDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.colors(
                            containerColor = TvAccent,
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = TvAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Text(text = stringResource(R.string.btn_retry), fontSize = 22.sp)
                    }

                    OutlinedButton(
                        onClick = onDone,
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)
                    ) {
                        Text(text = stringResource(R.string.btn_back), fontSize = 22.sp)
                    }
                }
            }
        }
    }

    LaunchedEffect(state) {
        if (state == ConnectState.Success || state == ConnectState.Failed) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun ConnectingScreenConnectingPreview() {
    WifiEapTheme {
        ConnectingScreen(
            ssid = "Corp-WiFi",
            state = ConnectState.Connecting,
            onRetry = {},
            onDone = {}
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun ConnectingScreenSuccessPreview() {
    WifiEapTheme {
        ConnectingScreen(
            ssid = "Corp-WiFi",
            state = ConnectState.Success,
            onRetry = {},
            onDone = {}
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun ConnectingScreenSuggestionPreview() {
    WifiEapTheme {
        ConnectingScreen(
            ssid = "Corp-WiFi",
            state = ConnectState.Success,
            isSuggestionMode = true,
            onRetry = {},
            onDone = {}
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun ConnectingScreenFailedPreview() {
    WifiEapTheme {
        ConnectingScreen(
            ssid = "Corp-WiFi",
            state = ConnectState.Failed,
            errorMessage = "addNetwork failed\nsdk=30, device=Xiaomi MiTV, eap=TTLS, phase2=PAP",
            onRetry = {},
            onDone = {}
        )
    }
}
