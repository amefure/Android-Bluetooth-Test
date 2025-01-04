package com.amefure.mybluetoothtest

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.amefure.mybluetoothtest.BLE.BleActiveStateManager
import com.amefure.mybluetoothtest.BLE.BleServiceConfig
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException

/**
 * ①：Ble有効状態(サポート対象&パーミッション)をチェック
 * ②：スキャン機能の実装(デバイスアドレスの取得)
 * ③：接続処理
 * ④：サービスとキャラクタリスティックの検索を開始
 * ⑤：
 * ⑥：
 */
@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private lateinit var logArea: TextView
    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var bleActiveStateManager: BleActiveStateManager

    /** ② ペリフェラルデバイスのスキャンを行えるクラス */
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    /** ② スキャン結果のコールバック */
    private var scanCallback: ScanCallback? = null
    /** ② GATT (Generic Attribute Profile) プロトコルを使用してデバイスと通信するためのクラス */
    private var bluetoothGatt: BluetoothGatt? = null
    /** ① Bluetoothの有効状態やペアリング済みデバイスの取得などを行えるクラス */
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ローカル保存セットアップ
        sharedPreferencesManager = SharedPreferencesManager(this)
        sharedPreferencesManager.setUp()

        // Bluetooth有効状態を監視
        observeBleState()

        // UIセットアップ
        setUpUI()
    }

    /** ① Ble有効状態をチェック & 観測 */
    private fun observeBleState() {
        // Bluetooth有効確認クラス
        bleActiveStateManager = BleActiveStateManager(this, bluetoothAdapter)

        // Bluetoothが有効な状態かチェック
        bleActiveStateManager.checking()

        // Bluetooth有効状態を監視
        lifecycleScope.launch {
            bleActiveStateManager.permissionState.collect { state ->
                when (state) {
                    is BleActiveStateManager.BluetoothState.Initial -> {
                        Toast.makeText(this@MainActivity, "初期状態", Toast.LENGTH_LONG).show()
                    }

                    is BleActiveStateManager.BluetoothState.Active -> {
                        Toast.makeText(this@MainActivity, "許可された", Toast.LENGTH_LONG).show()
                    }

                    is BleActiveStateManager.BluetoothState.NotSupport -> {
                        Toast.makeText(this@MainActivity, "非サポートデバイス", Toast.LENGTH_LONG).show()
                    }

                    is BleActiveStateManager.BluetoothState.Denied -> {
                        Toast.makeText(this@MainActivity, "否認された", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setUpUI() {
        // ログ表示ビュー
        logArea = findViewById(R.id.log)
        val connectButton: Button = findViewById(R.id.connect_button)
        val disConnectButton: Button = findViewById(R.id.disconnect_button)
        val scanButton: Button = findViewById(R.id.scan_button)
        // ペリフェラルとのスキャン処理
        scanButton.setOnClickListener {
            startScan()
        }
        // ペリフェラルとの接続処理
        connectButton.setOnClickListener { }
        // ペリフェラルとの切断処理
        disConnectButton.setOnClickListener { }
    }


    /**
     *  ② ペリフェラルスキャン開始
     *  https://appdev-room.com/android-bluetooth-scan
     */
    private fun startScan() {
        // スキャン対象のペリフェラルサービスUUIDをフィルタリング
        val scanFilters = listOf(BleServiceConfig.SERVICE_UUID)
            .map {
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(it))
                    .build()
            }

        // SCAN_MODE_BALANCED：検出効率とエネルギー消費の適度なバランスを維持したスキャン
        val scanSettings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        scanCallback = leScanCallback()
        logArea.text = "スキャン開始\n"
        // スキャンの開始
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
    }

    /** ②　スキャンコールバック */
    private fun leScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                super.onScanResult(callbackType, result)
                result.device ?: return
                // ペリフェラルデバイスが発見された
                logArea.append( "デバイス「${result.device.name}」を検出\n")
                // スキャンの停止
                bluetoothLeScanner?.stopScan(scanCallback)
                // デバイスアドレスを取得(接続処理に必要)
                val deviceAddress = result.device.address
                // 接続処理実行
                connect(deviceAddress)
            }
        }
    }

    /** ③ デバイスアドレスを元に接続処理 */
    private fun connect(address: String) {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)

        if (device == null) {
            logArea.append("デバイス取得失敗\n")
            return
        }
        logArea.append("対象デバイスと接続開始\n")
        bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
    }

    /** ③ 接続 & 通信コールバック */
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        /** ペリフェラルとの接続状態が変化した際に呼ばれる */
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            // STATE_CONNECTEDなら接続成功
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logArea.append("接続成功\n")
                // サービスの検索を開始
                bluetoothGatt?.discoverServices()
            }
        }

        /** サービスが検出された時に呼ばれる */
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            logArea.append("サービス発見\n")
            // 対象のサービス(BluetoothGattService)を取得
            val service: BluetoothGattService = gatt!!.getService(BleServiceConfig.SERVICE_UUID)
        }
    }
}