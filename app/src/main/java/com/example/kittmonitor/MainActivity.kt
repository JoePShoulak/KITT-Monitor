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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import com.example.kittmonitor.ui.theme.KITTMonitorTheme
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val statusTextState = mutableStateOf("")
    private val isConnectedState = mutableStateOf(false)
    private val logMessages = mutableStateListOf<AnnotatedString>()

    private val descriptorQueue =
        ConcurrentLinkedQueue<Pair<BluetoothGatt, BluetoothGattDescriptor>>()
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(padding)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = statusTextState.value,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (isConnectedState.value) {
                            TerminalView(
                                logs = logMessages,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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
        isConnectedState.value = false
        logMessages.clear()
        attemptFullConnection(
            onStatusChange = { statusTextState.value = it },
            onDisconnected = {
                statusTextState.value = "Disconnected"
                isConnectedState.value = false
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
                        isConnectedState.value = false
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
                val raw = value?.decodeToString() ?: ""
                Log.d(
                    "KITTMonitor",
                    "Received update: $raw"
                )
                val formatted = formatMessage(characteristic.uuid, raw)
                runOnUiThread {
                    logMessages.add(formatted)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
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

    private fun formatMessage(uuid: UUID, value: String): AnnotatedString {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val white = Color.White
        return buildAnnotatedString {
            withStyle(SpanStyle(color = white)) { append("[$timestamp] ") }
            when {
                uuid.toString().uppercase().endsWith("DA70") -> {
                    withStyle(SpanStyle(color = Color.Blue)) { append("DAT ") }
                    withStyle(SpanStyle(color = white)) { append("Voltage: $value") }
                    withStyle(SpanStyle(color = white)) { append("V") }
                }
                uuid.toString().uppercase().endsWith("CBAD") -> {
                    withStyle(SpanStyle(color = Color.Red)) { append("ERR ") }
                    withStyle(SpanStyle(color = white)) { append(value) }
                }
                else -> {
                    withStyle(SpanStyle(color = white)) { append(value) }
                }
            }
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
                UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5ECBAD"), // Errors
                UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EDA70")  // Voltage
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

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            beginConnectionFlow()
        }
    }
}

@Composable
fun TerminalView(logs: List<AnnotatedString>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var followBottom by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(logs.size, followBottom) {
        if (followBottom && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisible < logs.size - 1) {
                        followBottom = false
                    }
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    followBottom = true
                    scope.launch { listState.animateScrollToItem(logs.size - 1) }
                })
            }
    ) {
        items(logs) { message ->
            Text(
                text = message,
                color = Color.Unspecified,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
