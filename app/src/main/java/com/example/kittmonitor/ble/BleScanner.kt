package com.example.kittmonitor.ble

import android.content.Context
import com.juul.simpleble.SimpleBle
import com.juul.simpleble.scan.Advertisement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BleScanner(context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _devices = MutableStateFlow<List<Advertisement>>(emptyList())
    val devices: StateFlow<List<Advertisement>> = _devices

    private val scanner = SimpleBle.scanner(context)

    fun startScan() {
        scope.launch {
            scanner.advertisements.collect { adv ->
                _devices.value = (_devices.value + adv).distinctBy { it.address }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
