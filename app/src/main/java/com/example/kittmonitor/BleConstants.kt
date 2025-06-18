package com.example.kittmonitor

import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EFEED")
    val ERROR_UUID: UUID = UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5ECBAD")
    val DATA_UUID: UUID = UUID.fromString("1982C0DE-D00D-1123-BEEF-C0DEBA5EDA7A")
    const val DEVICE_NAME = "KITT"
}
