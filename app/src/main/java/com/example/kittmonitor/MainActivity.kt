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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.kittmonitor.ui.theme.KITTMonitorTheme
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val statusTextState = mutableStateOf("")

    private val descriptorQueue = ConcurrentLinkedQueue<Pair<BluetoothGatt, BluetoothGattDescriptor>>()
    private var isWritingDescriptor = false

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
                        beginConnectionFlow()
                    }
                }
            }
        }
    }

    private fun beginConnectionFlow() {
        statusTextState.value = "Searching..."
        attemptFullConnection(
            onStatusChange = { statusTextState.value = it },
            onDisconnected = {
                statusTextState.value = "Disconnected"
            }
        )
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
    private fun attemptFullConnection(
        onStatusChange: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
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
                        createGattCallback(onStatusChange, onDisconnected)
                    )
                }
            }
        }
        scanner?.startScan(scanCallback)
    }

    private fun createGattCallback(
        onStatusChange: (String) -> Unit,
        onDisconnected: () -> Unit
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                Log.d("KITTMonitor", "onConnectionStateChange: status=$status, newState=$newState")
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
                handleServiceDiscovery(gatt, status, onStatusChange)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val value = characteristic.value
                Log.d(
                    "KITTMonitor",
                    "Received update from ${characteristic.uuid}: ${value?.decodeToString()}"
                )
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                isWritingDescriptor = false
                processNextDescriptor()
            }
        }
    }

    private fun enqueueDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        descriptorQueue.add(Pair(gatt, descriptor))
        if (!isWritingDescriptor) {
            processNextDescriptor()
        }
    }

    private fun processNextDescriptor() {
        val item = descriptorQueue.poll()
        if (item != null) {
            isWritingDescriptor = true
            val (gatt, descriptor) = item
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleServiceDiscovery(
        gatt: BluetoothGatt,
        status: Int,
        onStatusChange: (String) -> Unit
    ) {
        Log.d("KITTMonitor", "onServicesDiscovered: status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS && hasPermission()) {
            runOnUiThread { onStatusChange("Subscribing...") }
            val targetServiceUUID = UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EFEED")
            val targetCharUUIDs = setOf(
                UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5ECBAD"),
                UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EDA7A")
            )

            val service = gatt.getService(targetServiceUUID)
            if (service != null) {
                targetCharUUIDs.forEach { uuid ->
                    val characteristic = service.getCharacteristic(uuid)
                    if (characteristic != null &&
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    ) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            enqueueDescriptorWrite(gatt, it)
                        }
                    }
                }
                statusTextState.value = "Completed!"
            } else {
                Log.w("KITTMonitor", "Target service not found, restarting...")
                gatt.disconnect()
            }
        } else {
            Log.e("KITTMonitor", "Service discovery failed with status $status")
            gatt.disconnect()
        }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            beginConnectionFlow()
        }
    }
}
