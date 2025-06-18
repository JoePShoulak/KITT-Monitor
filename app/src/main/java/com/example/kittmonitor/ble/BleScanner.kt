package com.example.kittmonitor.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleScanner(private val context: Context) {
    private val scanner: BluetoothLeScanner by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter.bluetoothLeScanner
    }

    fun scan(): Flow<List<BleDevice>> = callbackFlow {
        val devices = mutableListOf<BleDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown"
                val address = device.address
                if (devices.none { it.address == address }) {
                    devices.add(BleDevice(name, address))
                    trySend(devices.toList())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(RuntimeException("Scan failed with error $errorCode"))
            }
        }
        scanner.startScan(callback)
        awaitClose { scanner.stopScan(callback) }
    }
}
