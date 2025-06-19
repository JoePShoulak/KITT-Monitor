package com.example.kittmonitor

import android.bluetooth.*
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.ui.text.AnnotatedString
import java.util.UUID
import androidx.compose.ui.text.withStyle

fun createGattCallback(
    hasPermission: () -> Boolean,
    descriptorQueue: DescriptorWriteQueue,
    logMessages: MutableList<AnnotatedString>,
    isConnectedState: MutableState<Boolean>,
    attemptReconnect: () -> Unit,
    onDisconnected: () -> Unit
): BluetoothGattCallback {
    fun handleServiceDiscovery(gatt: BluetoothGatt, status: Int) {
        Log.d("KITTMonitor", "onServicesDiscovered: status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS && hasPermission()) {
            val targetServiceUUID = UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EFEED")
            val targetCharUUIDs = setOf(
                UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5ECBAD"),
                UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EDA70")
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
                            descriptorQueue.enqueue(gatt, it)
                        }
                    }
                }
                isConnectedState.value = true
            } else {
                Log.w("KITTMonitor", "Target service not found, restarting...")
                gatt.disconnect()
            }
        } else {
            Log.e("KITTMonitor", "Service discovery failed with status $status")
            gatt.disconnect()
        }
    }

    return object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d("KITTMonitor", "onConnectionStateChange: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && hasPermission()) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onDisconnected()
                isConnectedState.value = false
                attemptReconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handleServiceDiscovery(gatt, status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value
            val raw = value?.decodeToString() ?: ""
            Log.d("KITTMonitor", "Received update: $raw")
            val formatted = formatMessage(characteristic.uuid, raw)
            logMessages.add(formatted)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            descriptorQueue.onWriteComplete()
        }
    }
}

fun formatMessage(uuid: UUID, value: String): AnnotatedString {
    val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
    val white = androidx.compose.ui.graphics.Color.White
    return androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = white)) { append("[$timestamp] ") }
        when {
            uuid.toString().uppercase().endsWith("DA70") -> {
                withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Cyan)) { append("DAT ") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = white)) { append("Voltage: $value") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = white)) { append("V") }
            }
            uuid.toString().uppercase().endsWith("CBAD") -> {
                withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Yellow)) { append("ERR ") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = white)) { append(value) }
            }
            else -> {
                withStyle(androidx.compose.ui.text.SpanStyle(color = white)) { append(value) }
            }
        }
    }
}
