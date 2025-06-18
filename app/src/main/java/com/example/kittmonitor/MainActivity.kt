package com.example.kittmonitor

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.kittmonitor.ui.theme.KITTMonitorTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val status = mutableStateOf("Scanning for device...")
    private val bluetoothManager by lazy { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (hasPermissions()) {
            startScan()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions(), 1)
        }

        setContent {
            KITTMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text(
                        text = status.value,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            status.value = "Bluetooth permission denied"
        }
    }

    private fun startScan() {
        status.value = "Scanning for device..."
        val filter = ScanFilter.Builder().setDeviceName(BleConstants.DEVICE_NAME).build()
        val settings = ScanSettings.Builder().build()
        bluetoothAdapter.bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == BleConstants.DEVICE_NAME) {
                status.value = "Device found, connecting..."
                bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                result.device.connectGatt(this@MainActivity, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                runOnUiThread { status.value = "Connected, discovering services..." }
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            val service = gatt.getService(BleConstants.SERVICE_UUID) ?: return
            val dataChar = service.getCharacteristic(BleConstants.DATA_UUID)
            val errorChar = service.getCharacteristic(BleConstants.ERROR_UUID)
            enableNotifications(gatt, dataChar)
            enableNotifications(gatt, errorChar)
            runOnUiThread { status.value = "Waiting for data..." }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val msg = characteristic.getStringValue(0) ?: ""
            runOnUiThread {
                if (characteristic.uuid == BleConstants.DATA_UUID) {
                    status.value = "Data: $msg"
                } else if (characteristic.uuid == BleConstants.ERROR_UUID) {
                    status.value = "Error: $msg"
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }
}

@Composable
@Preview(showBackground = true)
fun GreetingPreview() {
    val state = remember { mutableStateOf("Preview") }
    KITTMonitorTheme {
        Text(text = state.value)
    }
}
