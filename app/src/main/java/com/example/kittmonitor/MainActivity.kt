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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FolderOpen
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import android.widget.Toast
import android.app.AlertDialog
import androidx.core.content.FileProvider
import android.net.Uri
import androidx.annotation.RequiresApi
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import com.example.kittmonitor.ui.theme.KITTMonitorTheme
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import android.os.Environment

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val statusTextState = mutableStateOf("")
    private val isConnectedState = mutableStateOf(false)
    private val logMessages = mutableStateListOf<AnnotatedString>()
    private val followBottomState = mutableStateOf(true)

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color.DarkGray)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (isConnectedState.value) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { saveLogsToFile() }) {
                                            Icon(
                                                imageVector = Icons.Default.Save,
                                                contentDescription = "Save logs",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(onClick = { openLogsDirectory() }) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = "Open logs directory",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = statusTextState.value,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (isConnectedState.value) {
                                    Switch(
                                        checked = followBottomState.value,
                                        onCheckedChange = { followBottomState.value = it }
                                    )
                                }
                            }
                        }
                        if (isConnectedState.value) {
                            TerminalView(
                                logs = logMessages,
                                followBottom = followBottomState.value,
                                onFollowBottomChange = { followBottomState.value = it },
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
        followBottomState.value = true
        logMessages.clear()
        attemptFullConnection(
            onStatusChange = { statusTextState.value = it },
            onDisconnected = {
                statusTextState.value = "Disconnected"
                isConnectedState.value = false
                followBottomState.value = true
            }
        )
    }

    private fun hasPermission(): Boolean {
        val basePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val storagePerms = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> emptyArray()
        }

        val perms = basePerms + storagePerms
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsIfNeeded() {
        val basePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val storagePerms = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> emptyArray()
        }

        val perms = basePerms + storagePerms
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveLogsToFile() {
        val text = logMessages.joinToString("\n") { it.text }
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(baseDir, "Kitt_Folder")
        if (!dir.exists()) dir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"))
        val file = File(dir, "log_${timestamp}.txt")
        try {
            file.writeText(text)
            logMessages.clear()
            Log.d("KITTMonitor", "Logs saved to ${file.absolutePath}")
            showFileDialog(file)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save logs", Toast.LENGTH_LONG).show()
            Log.e("KITTMonitor", "Failed to save logs", e)
        }
    }

    private fun showFileDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Logs saved")
            .setMessage(file.absolutePath)
            .setNegativeButton("Close", null)
            .setPositiveButton("View File") { _, _ -> openFile(file) }
            .show()
    }

    private fun openFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(intent, "Open file"))
    }

    private fun openLogsDirectory() {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(baseDir, "Kitt_Folder")
        if (!dir.exists()) dir.mkdirs()
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", dir)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open directory"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open directory", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatMessage(uuid: UUID, value: String): AnnotatedString {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val white = Color.White
        return buildAnnotatedString {
            withStyle(SpanStyle(color = white)) { append("[$timestamp] ") }
            when {
                uuid.toString().uppercase().endsWith("DA70") -> {
                    withStyle(SpanStyle(color = Color.Cyan)) { append("DAT ") }
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
                statusTextState.value = "KITT Monitor"
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
fun TerminalView(
    logs: List<AnnotatedString>,
    followBottom: Boolean,
    onFollowBottomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var programmaticScroll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(logs.size, followBottom) {
        if (followBottom && logs.isNotEmpty()) {
            programmaticScroll = true
            listState.animateScrollToItem(logs.size - 1)
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress && !programmaticScroll) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisible < logs.size - 1) {
                        onFollowBottomChange(false)
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
                    onFollowBottomChange(true)
                    programmaticScroll = true
                    scope.launch {
                        listState.animateScrollToItem(logs.size - 1)
                        programmaticScroll = false
                    }
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

