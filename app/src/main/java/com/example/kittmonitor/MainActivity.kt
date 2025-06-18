package com.example.kittmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.kittmonitor.ui.theme.KITTMonitorTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        if (hasPermission()) {
            scanner = bluetoothAdapter.bluetoothLeScanner
        }

        requestPermissionsIfNeeded()

        setContent {
            var connected by remember { mutableStateOf(false) }

            KITTMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (!hasPermission()) return@Button
                                scanCallback = object : ScanCallback() {
                                    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
                                    override fun onScanResult(type: Int, result: ScanResult) {
                                        if (!hasPermission()) return
                                        val name = result.device.name ?: ""
                                        if (name == "KITT") {
                                            scanner?.stopScan(this)
                                            gatt = result.device.connectGatt(this@MainActivity, false, object : BluetoothGattCallback() {
                                                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                                                    if (newState == BluetoothProfile.STATE_CONNECTED && hasPermission()) {
                                                        connected = true
                                                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                                        connected = false
                                                    }
                                                }
                                            })
                                        }
                                    }
                                }
                                scanner?.startScan(scanCallback)
                            },
                            enabled = !connected
                        ) {
                            Text(if (connected) "Connected" else "Connect to KITT")
                        }
                    }
                }
            }
        }
    }

    private fun hasPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)

        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }
}
