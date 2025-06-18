package com.example.kittmonitor.ble

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScannerScreen(viewModel: BleViewModel = viewModel()) {
    val devices by viewModel.devices.collectAsState()

    Column {
        Button(onClick = { viewModel.startScanning() }, modifier = Modifier.fillMaxWidth()) {
            Text("Start Scan")
        }
        LazyColumn {
            items(devices) { device ->
                Text(text = "${device.name} (${device.address})")
            }
        }
    }
}
