package com.amefure.mybluetoothtest

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
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
 * ⑤：Read & Write & Notifyキャラクタリスティックを実装
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

    /** ④ キャラクタリスティック */
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

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
        connectButton.setOnClickListener {
            // ローカル保存したアドレスを取得
            // val deviceAddress = sharedPreferencesManager.fetch(SharedPreferencesManager.ADDRESS_KEY)
            // ボンディング済みデバイスからデバイスアドレスを取得
            val deviceAddress = bluetoothAdapter?.bondedDevices?.firstOrNull { it.name == BleServiceConfig.PERIPHERAL_NAME }?.address
            if (deviceAddress != null) {
                connect(deviceAddress)
            } else {
                logArea.append("スキャン&ペアリングしてデバイスアドレスを取得してください\n")
            }
        }
        // ペリフェラルとの切断処理
        disConnectButton.setOnClickListener {
            disconnect()
        }

        val readButton: Button = findViewById(R.id.reed_button)
        val writeButton: Button = findViewById(R.id.write_button)
        val notifyButton: Button = findViewById(R.id.notify_button)

        readButton.setOnClickListener {
            read()
        }

        writeButton.setOnClickListener {
            write()
        }

        notifyButton.setOnClickListener {
            observeNotify()
        }
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
                logArea.append( "デバイス「${result.device.name}」「${result.device.address}」を検出\n")
                // スキャンの停止
                bluetoothLeScanner?.stopScan(scanCallback)
                // デバイスアドレスを取得(接続処理に必要)
                val deviceAddress = result.device.address
                // ローカルにデバイスアドレスを保存
                // sharedPreferencesManager.save(SharedPreferencesManager.ADDRESS_KEY, deviceAddress)
                // 対象機器とボンディングする
                result.device.createBond()
                logArea.append( "スキャン停止\n")
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

        /** ④ サービスが検出された時に呼ばれる */
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?: return
            logArea.append("サービス発見：${gatt.services.size}個 \n")
            // 対象のサービス(BluetoothGattService)を取得
            val service: BluetoothGattService = gatt.getService(BleServiceConfig.SERVICE_UUID)
            readCharacteristic = service.getCharacteristic(BleServiceConfig.READ_CHARACTERISTIC_UUID)
            if (readCharacteristic != null) {
                logArea.append( "Read Characteristic取得成功\n")
            }
            writeCharacteristic = service.getCharacteristic(BleServiceConfig.WRITE_CHARACTERISTIC_UUID)
            if (writeCharacteristic != null) {
                logArea.append( "Write Characteristic取得成功\n")
            }
            notifyCharacteristic = service.getCharacteristic(BleServiceConfig.NOTIFY_CHARACTERISTIC_UUID)
            if (notifyCharacteristic != null) {
                logArea.append( "Notify Characteristic取得成功\n")
            }
        }

        /** ④ Readキャラクタリスティック */
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logArea.append("読み取り成功\n")
                try {
                    // UTF-8エンコーディングを使用してバイト配列を文字列に変換
                    val data = String(value, Charsets.UTF_8)
                    // 変換された文字列を使用
                    logArea.append("読み取りデータ：${data}\n")
                } catch (e: UnsupportedEncodingException) {
                    // エンコーディングがサポートされていない場合の例外処理
                    e.printStackTrace()
                }
            } else {
                logArea.append("読み取り失敗\n")
            }
        }

        /** ④ Writeキャラクタリスティック */
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logArea.append("書き込み成功\n")
            } else {
                logArea.append("書き込み失敗\n")
            }
        }

        /** ④ Notifyキャラクタリスティック */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            logArea.append("Notify変化検知\n")
        }
    }

    /** ⑤ Readキャラクタリスティックの実行 */
    private fun read() {
        bluetoothGatt?.let { gatt ->
            logArea.append("Readメソッド実行\n")
            gatt.readCharacteristic(readCharacteristic)
        }
    }

    /** ⑤ Writeキャラクタリスティックの実行 */
    private fun write() {
        bluetoothGatt?.let { gatt ->
            logArea.append("Write実行\n")
            // 文字列
            val str = "Hello World"

            try {
                // UTF-8エンコーディングを使用して文字列をバイト配列に変換
                val byteData = str.toByteArray(Charsets.UTF_8)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    writeCharacteristic ?: return
                    gatt.writeCharacteristic(writeCharacteristic!!, byteData, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    writeCharacteristic?.value = byteData
                    writeCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    gatt.writeCharacteristic(writeCharacteristic)
                }
            } catch (e: UnsupportedEncodingException) {
                // エンコーディングがサポートされていない場合の例外処理
                e.printStackTrace()
            }
        }
    }

    /** ⑤ Notifyキャラクタリスティックの実行(観測開始) */
    private fun observeNotify() {
        bluetoothGatt?.let { gatt ->
            logArea.append("Notify観測開始\n")
            gatt.setCharacteristicNotification(notifyCharacteristic, true)
        }
    }

    private fun disconnect() {
        // デバイスとの接続を解除
        bluetoothGatt?.disconnect()
        // リソースを解放
        bluetoothGatt?.close()
        // GATTインスタンスをクリア
        bluetoothGatt = null
        logArea.text = "切断\n"
    }
}