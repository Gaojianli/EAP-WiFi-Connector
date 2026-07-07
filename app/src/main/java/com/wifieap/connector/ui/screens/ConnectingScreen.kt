package com.wifieap.connector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.wifieap.connector.ui.ConnectState
import com.wifieap.connector.ui.theme.TvAccent
import com.wifieap.connector.ui.theme.TvBackground
import com.wifieap.connector.ui.theme.TvError
import com.wifieap.connector.ui.theme.TvOnSurfaceDim
import com.wifieap.connector.ui.theme.TvSuccess
import com.wifieap.connector.ui.theme.TvSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConnectingScreen(
    ssid: String,
    state: ConnectState?,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onDisconnect: () -> Unit
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
                    text = "正在连接...",
                    fontSize = 24.sp,
                    color = TvOnSurfaceDim
                )
            }
            ConnectState.Success -> {
                Text(
                    text = "连接成功",
                    fontSize = 32.sp,
                    color = TvSuccess,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        onClick = onDone,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvAccent,
                            focusedContainerColor = TvAccent.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .focusRequester(focusRequester)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp)
                        ) {
                            Text(text = "完成", fontSize = 22.sp, color = Color.White)
                        }
                    }

                    Surface(
                        onClick = onDisconnect,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvSurface,
                            focusedContainerColor = TvError.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp)
                        ) {
                            Text(text = "断开连接", fontSize = 22.sp, color = Color.White)
                        }
                    }
                }
            }
            ConnectState.Failed -> {
                Text(
                    text = "连接失败",
                    fontSize = 32.sp,
                    color = TvError,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        onClick = onRetry,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvAccent,
                            focusedContainerColor = TvAccent.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .focusRequester(focusRequester)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp)
                        ) {
                            Text(text = "重试", fontSize = 22.sp, color = Color.White)
                        }
                    }

                    Surface(
                        onClick = onDone,
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
                            Text(text = "返回", fontSize = 22.sp, color = Color.White)
                        }
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
