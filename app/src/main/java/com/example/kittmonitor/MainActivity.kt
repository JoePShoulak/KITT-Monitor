package com.example.kittmonitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kittmonitor.ui.theme.KITTMonitorTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        requestPermissionsIfNeeded()

        val requestEnableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // User returned from enable Bluetooth dialog
        }

        setContent {
            val bluetoothStatus = remember { mutableStateOf(getBluetoothStatus()) }
            var isScanning by remember { mutableStateOf(false) }
            var foundKITT by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                while (true) {
                    bluetoothStatus.value = getBluetoothStatus()
                    kotlinx.coroutines.delay(1000)
                }
            }

            KITTMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Greeting(status = bluetoothStatus.value)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(text = if (foundKITT) "✅ KITT found!" else "🔍 Looking for KITT...")

                        Spacer(modifier = Modifier.height(16.dp))

                        if (bluetoothStatus.value.contains("OFF")) {
                            Button(onClick = {
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                requestEnableBluetooth.launch(intent)
                            }) {
                                Text("Enable Bluetooth")
                            }
                        } else {
                            Button(onClick = {
                                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                    return@Button
                                }

                                if (isScanning) {
                                    bluetoothLeScanner?.stopScan(scanCallback)
                                } else {
                                    foundKITT = false
                                    scanCallback = object : ScanCallback() {
                                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                                            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                                                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                                                return
                                            }

                                            val name = result.device.name ?: "Unnamed"
                                            if (name == "KITT") {
                                                foundKITT = true
                                                bluetoothLeScanner?.stopScan(this)
                                                isScanning = false
                                            }
                                        }
                                    }
                                    bluetoothLeScanner?.startScan(scanCallback)
                                }
                                isScanning = !isScanning
                            }) {
                                Text(
                                    when {
                                        foundKITT -> "Restart Scan"
                                        isScanning -> "Stop Scan"
                                        else -> "Start Scan"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getBluetoothStatus(): String {
        return if (bluetoothAdapter.isEnabled) {
            "Bluetooth is ON"
        } else {
            "Bluetooth is OFF. Please enable it."
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1)
        }
    }
}

@Composable
fun Greeting(status: String, modifier: Modifier = Modifier) {
    Text(
        text = status,
        modifier = modifier
    )
}
