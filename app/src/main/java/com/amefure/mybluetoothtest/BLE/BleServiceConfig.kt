package com.amefure.mybluetoothtest.BLE

import java.util.UUID

/**
 * ペリフェラルUUID
 * iOSで定義したものと同じものを使用
 * https://github.com/amefure/iOS-Bluetooth-App/blob/main/MyBluetoothTest/CoreBLE/BluePeripheralManager.swift#L35
 */
object BleServiceConfig {
    val SERVICE_UUID: UUID = UUID.fromString("00000000-0000-1111-1111-111111111111")
    val READ_CHARACTERISTIC_UUID: UUID = UUID.fromString("00000000-1111-1111-1111-111111111111")
    val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00000000-2222-1111-1111-111111111111")
    val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("00000000-3333-1111-1111-111111111111")
    val INDICATE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00000000-4444-1111-1111-111111111111")
}
