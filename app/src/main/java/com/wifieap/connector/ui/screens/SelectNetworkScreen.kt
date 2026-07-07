package com.wifieap.connector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.wifieap.connector.WifiNetwork
import com.wifieap.connector.ui.theme.TvAccent
import com.wifieap.connector.ui.theme.TvBackground
import com.wifieap.connector.ui.theme.TvOnSurfaceDim
import com.wifieap.connector.ui.theme.TvSurface

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
            text = "选择企业 Wi-Fi 网络",
            fontSize = 32.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isScanning) {
            Text(
                text = "正在扫描...",
                fontSize = 18.sp,
                color = TvOnSurfaceDim,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (networks.isEmpty() && !isScanning) {
            Text(
                text = "未找到企业 Wi-Fi 网络",
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
                onClick = onRefresh,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvSurface,
                    focusedContainerColor = TvAccent
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "刷新",
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
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "手动输入 / 隐藏网络",
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
            Text(
                text = network.ssid,
                fontSize = 22.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = getSignalLabel(network.level),
                fontSize = 18.sp,
                color = TvOnSurfaceDim
            )
        }
    }
}

private fun getSignalLabel(level: Int): String {
    return when {
        level >= -50 -> "信号: 极强"
        level >= -60 -> "信号: 强"
        level >= -70 -> "信号: 中"
        else -> "信号: 弱"
    }
}
