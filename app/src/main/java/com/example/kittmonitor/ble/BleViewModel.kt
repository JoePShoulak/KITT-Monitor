package com.example.kittmonitor.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = BleScanner(application)

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    fun startScanning() {
        viewModelScope.launch {
            scanner.scan().collect { list ->
                _devices.value = list
            }
        }
    }
}
