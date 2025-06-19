package com.example.kittmonitor

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import java.util.concurrent.ConcurrentLinkedQueue

class DescriptorWriteQueue {
    private val queue = ConcurrentLinkedQueue<Pair<BluetoothGatt, BluetoothGattDescriptor>>()
    private var isWriting = false

    fun enqueue(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        queue.add(Pair(gatt, descriptor))
        if (!isWriting) {
            processNext()
        }
    }

    fun onWriteComplete() {
        isWriting = false
        processNext()
    }

    private fun processNext() {
        val item = queue.poll()
        if (item != null) {
            isWriting = true
            val (gatt, descriptor) = item
            gatt.writeDescriptor(descriptor)
        }
    }
}
