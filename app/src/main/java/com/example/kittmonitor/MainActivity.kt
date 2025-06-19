@file:Suppress("DEPRECATION")

package com.example.kittmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val statusTextState = mutableStateOf("")
    private val isConnectedState = mutableStateOf(false)
    private val logMessages = mutableStateListOf<AnnotatedString>()
    private val followBottomState = mutableStateOf(true)

    private val descriptorQueue = DescriptorWriteQueue()

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        requestPermissionsIfNeeded()

        setContent {
            MainScreen(
                isConnectedState = isConnectedState,
                statusTextState = statusTextState,
                logMessages = logMessages,
                followBottomState = followBottomState,
                bluetoothAdapter = bluetoothAdapter,
                saveLogsToFile = ::saveLogsToFile,
                openLogsFolder = ::openLogsFolder,
                hasPermission = ::hasPermission,
                beginConnectionFlow = ::beginConnectionFlow
            )
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
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

        val storagePerms = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else emptyArray()

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

        val storagePerms = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else emptyArray()

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
                        createGattCallback(
                            hasPermission = ::hasPermission,
                            descriptorQueue = descriptorQueue,
                            logMessages = logMessages,
                            isConnectedState = isConnectedState,
                            statusTextState = statusTextState,
                            attemptReconnect = { attemptFullConnection(onStatusChange, onDisconnected) },
                            onStatusChange = onStatusChange,
                            onDisconnected = onDisconnected
                        )
                    )
                }
            }
        }
        scanner?.startScan(scanCallback)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveLogsToFile() {
        val text = logMessages.joinToString("\n") { it.text }
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docsDir, "KITT Logs")
        if (!dir.exists()) dir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"))
        val file = File(dir, "log_${'$'}timestamp.txt")
        try {
            file.writeText(text)
            logMessages.clear()
            Log.d("KITTMonitor", "Logs saved to ${'$'}{file.absolutePath}")
            showFileDialog(file, "/Documents/${'$'}{dir.name}/${'$'}{file.name}")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save logs", Toast.LENGTH_LONG).show()
            Log.e("KITTMonitor", "Failed to save logs", e)
        }
    }

    private fun openLogsFolder() {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docsDir, "KITT Logs")
        if (!dir.exists()) dir.mkdirs()
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", dir)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(intent, "Open folder"))
    }

    private fun showFileDialog(file: File, displayPath: String) {
        AlertDialog.Builder(this)
            .setTitle("Logs saved")
            .setMessage(displayPath)
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

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            beginConnectionFlow()
        }
    }
}

