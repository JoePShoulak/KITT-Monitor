package com.example.kittmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.kittmonitor.ble.BleScanner
import com.juul.simpleble.scan.Advertisement
import com.example.kittmonitor.ui.theme.KITTMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scanner = BleScanner(this)
        setContent {
            KITTMonitorTheme {
                val devices by scanner.devices.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScanScreen(
                        devices = devices,
                        onStartScan = { scanner.startScan() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ScanScreen(devices: List<Advertisement>, onStartScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Button(onClick = onStartScan) {
            Text("Start Scan")
        }
        LazyColumn {
            items(devices) { device ->
                val name = device.name ?: "Unknown"
                Text("${'$'}name (${device.address})")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScanPreview() {
    KITTMonitorTheme {
        ScanScreen(devices = emptyList(), onStartScan = {})
    }
}