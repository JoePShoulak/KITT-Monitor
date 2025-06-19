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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import android.widget.Toast
import android.app.AlertDialog
import androidx.core.content.FileProvider
import android.net.Uri
import androidx.annotation.RequiresApi

import com.example.kittmonitor.DescriptorWriteQueue

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
                hasPermission = ::hasPermission,
                beginConnectionFlow = ::beginConnectionFlow
            )
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
                            attemptReconnect = {
                                attemptFullConnection(onStatusChange, onDisconnected)
                            },
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
        val dir = getExternalFilesDir(null)
        dir?.let {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"))
            val file = File(it, "log_${timestamp}.txt")
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

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            beginConnectionFlow()
        }
    }
}

