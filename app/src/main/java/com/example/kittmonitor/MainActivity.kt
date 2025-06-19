@file:Suppress("DEPRECATION")

package com.example.kittmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.kittmonitor.ui.theme.KITTMonitorTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null

    private val statusTextState = mutableStateOf("")

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        requestPermissionsIfNeeded()

        setContent {
            KITTMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(statusTextState.value, color = Color.White)
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission()) return@LaunchedEffect

                    if (!bluetoothAdapter.isEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(enableBtIntent, 1)
                    } else {
                        statusTextState.value = "Searching..."
                        attemptFullConnection(
                            onStatusChange = { statusTextState.value = it },
                            onDisconnected = {
                                statusTextState.value = "Disconnected"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun hasPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun attemptFullConnection(onStatusChange: (String) -> Unit, onDisconnected: () -> Unit) {
        startScan(onStatusChange, onDisconnected)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun startScan(onStatusChange: (String) -> Unit, onDisconnected: () -> Unit) {
        scanner = bluetoothAdapter.bluetoothLeScanner
        scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(type: Int, result: ScanResult) {
                if (!hasPermission()) return
                if (result.device.name == "KITT") {
                    scanner?.stopScan(this)
                    onStatusChange("Connecting...")

                    val currentContext = this@MainActivity

                    gatt = result.device.connectGatt(
                        currentContext,
                        false,
                        object : BluetoothGattCallback() {
                            override fun onConnectionStateChange(
                                g: BluetoothGatt,
                                status: Int,
                                newState: Int
                            ) {
                                Log.d(
                                    "KITTMonitor",
                                    "onConnectionStateChange: status=$status, newState=$newState"
                                )
                                if (newState == BluetoothProfile.STATE_CONNECTED && hasPermission()) {
                                    g.discoverServices()
                                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                    runOnUiThread {
                                        onDisconnected()
                                        attemptFullConnection(onStatusChange, onDisconnected)
                                    }
                                }
                            }

                            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                                Log.d("KITTMonitor", "onServicesDiscovered: status=$status")
                                if (status == BluetoothGatt.GATT_SUCCESS && hasPermission()) {
                                    var found = false
                                    gatt.services.forEach { service ->
                                        found = true
                                        Log.d("KITTMonitor", "Service: ${service.uuid}")
                                        service.characteristics.forEach { characteristic ->
                                            Log.d("KITTMonitor", "Characteristic: ${characteristic.uuid}")
                                        }
                                    }

                                    if (!found) {
                                        Log.w("KITTMonitor", "discovery timeout, restarting scan...")
                                        gatt.disconnect()
                                    } else {
                                        runOnUiThread { onStatusChange("Subscribing...") }
                                    }
                                } else {
                                    Log.e("KITTMonitor", "Service discovery failed with status $status")
                                    gatt.disconnect()
                                }
                            }
                        })
                }
            }
        }
        scanner?.startScan(scanCallback)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode != Activity.RESULT_OK) {
            finish() // Exit app if user refuses to enable Bluetooth
        } else {
            setContent {
                statusTextState.value = "Searching..."
                KITTMonitorTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(padding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(statusTextState.value, color = Color.White)
                        }
                    }

                    attemptFullConnection(
                        onStatusChange = { statusTextState.value = it },
                        onDisconnected = {
                            statusTextState.value = "Disconnected"
                        }
                    )
                }
            }
        }
    }
}
