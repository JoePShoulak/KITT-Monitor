package com.example.kittmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.kittmonitor.ui.theme.KITTMonitorTheme
import com.example.kittmonitor.ble.ScannerScreen

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN))
        enableEdgeToEdge()
        setContent {
            KITTMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    ScannerScreen()
                }
            }
        }
    }
}
