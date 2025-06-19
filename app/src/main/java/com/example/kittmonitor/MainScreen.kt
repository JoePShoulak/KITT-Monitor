package com.example.kittmonitor

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.kittmonitor.ui.theme.KITTMonitorTheme

@Composable
fun MainScreen(
    isConnectedState: MutableState<Boolean>,
    statusTextState: MutableState<String>,
    logMessages: MutableList<AnnotatedString>,
    followBottomState: MutableState<Boolean>,
    bluetoothAdapter: BluetoothAdapter,
    saveLogsToFile: () -> Unit,
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
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusTextState.value,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                    Spacer(modifier = Modifier.weight(1f))
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
