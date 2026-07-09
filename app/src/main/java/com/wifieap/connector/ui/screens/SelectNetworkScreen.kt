package com.wifieap.connector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.wifieap.connector.R
import com.wifieap.connector.WifiNetwork
import com.wifieap.connector.ui.theme.TvAccent
import com.wifieap.connector.ui.theme.TvBackground
import com.wifieap.connector.ui.theme.TvOnSurfaceDim
import com.wifieap.connector.ui.theme.TvSurface
import com.wifieap.connector.ui.theme.WifiEapTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SelectNetworkScreen(
    networks: List<WifiNetwork>,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onNetworkSelected: (String) -> Unit,
    onManualInput: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(48.dp)
    ) {
        Text(
            text = stringResource(R.string.title_select_network),
            fontSize = 32.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isScanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TvOnSurfaceDim,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scanning),
                    fontSize = 18.sp,
                    color = TvOnSurfaceDim
                )
            }
        }

        if (networks.isEmpty() && !isScanning) {
            Text(
                text = stringResource(R.string.no_networks_found),
                fontSize = 20.sp,
                color = TvOnSurfaceDim,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(networks) { network ->
                val itemModifier = if (network == networks.firstOrNull()) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
                NetworkItem(
                    network = network,
                    modifier = itemModifier,
                    onClick = { onNetworkSelected(network.ssid) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick = { if (!isScanning) onRefresh() },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvSurface,
                    focusedContainerColor = if (isScanning) TvSurface else TvAccent
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.refresh),
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }

            Surface(
                onClick = onManualInput,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvSurface,
                    focusedContainerColor = TvAccent
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.manual_input),
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
        }
    }

    LaunchedEffect(networks) {
        if (networks.isNotEmpty()) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NetworkItem(
    network: WifiNetwork,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val signalIcon = when {
        network.level >= -50 -> R.drawable.ic_signal_4
        network.level >= -60 -> R.drawable.ic_signal_3
        network.level >= -70 -> R.drawable.ic_signal_2
        else -> R.drawable.ic_signal_1
    }
    val encryptionLabel = parseEncryption(network.capabilities)

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurface,
            focusedContainerColor = TvAccent.copy(alpha = 0.3f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = network.ssid,
                    fontSize = 22.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = encryptionLabel,
                    fontSize = 14.sp,
                    color = TvOnSurfaceDim
                )
            }
            Icon(
                painter = painterResource(signalIcon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun parseEncryption(capabilities: String): String {
    return when {
        capabilities.contains("WPA3") && capabilities.contains("EAP") -> "WPA3-EAP"
        capabilities.contains("WPA2") && capabilities.contains("EAP") -> "WPA2-EAP"
        capabilities.contains("WPA") && capabilities.contains("EAP") -> "WPA-EAP"
        capabilities.contains("EAP") -> "802.1X"
        else -> "EAP"
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun SelectNetworkScreenPreview() {
    WifiEapTheme {
        SelectNetworkScreen(
            networks = listOf(
                WifiNetwork(ssid = "Corp-WiFi", level = -45, capabilities = "[WPA2-EAP]"),
                WifiNetwork(ssid = "Office-5G", level = -62, capabilities = "[WPA3-EAP]"),
                WifiNetwork(ssid = "Guest-EAP", level = -78, capabilities = "[WPA2-EAP]")
            ),
            isScanning = false,
            onRefresh = {},
            onNetworkSelected = {},
            onManualInput = {}
        )
    }
}
