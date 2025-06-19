package com.example.kittmonitor

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.ImageLoader
import coil.request.ImageRequest
import androidx.compose.runtime.remember
import android.os.Build
import com.example.kittmonitor.ui.theme.KITTMonitorTheme

@Composable
fun MainScreen(
    isConnectedState: MutableState<Boolean>,
    logMessages: MutableList<AnnotatedString>,
    followBottomState: MutableState<Boolean>,
    bluetoothAdapter: BluetoothAdapter,
    saveLogsToFile: () -> Unit,
    openLogsFolder: () -> Unit,
    hasPermission: () -> Boolean,
    beginConnectionFlow: () -> Unit
) {
    val activity = LocalContext.current as Activity

    KITTMonitorTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.DarkGray)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openLogsFolder() }) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Open logs folder",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (isConnectedState.value) {
                                IconButton(onClick = { saveLogsToFile() }) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save logs",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isConnectedState.value) {
                            Text(
                                text = "KITT Monitor",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (isConnectedState.value) {
                            Switch(
                                checked = followBottomState.value,
                                onCheckedChange = { followBottomState.value = it }
                            )
                        }
                    }
                }
                if (isConnectedState.value) {
                    TerminalView(
                        logs = logMessages,
                        followBottom = followBottomState.value,
                        onFollowBottomChange = { followBottomState.value = it },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val context = LocalContext.current
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .allowHardware(false)
                                .components {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        add(ImageDecoderDecoder.Factory())
                                    } else {
                                        add(GifDecoder.Factory())
                                    }
                                }
                                .build()
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.kitt)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!hasPermission()) return@LaunchedEffect

            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activity.startActivityForResult(enableBtIntent, 1)
            } else {
                beginConnectionFlow()
            }
        }
    }
}
