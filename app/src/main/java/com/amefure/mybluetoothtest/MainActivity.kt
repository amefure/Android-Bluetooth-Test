package com.amefure.mybluetoothtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.amefure.mybluetoothtest.BLE.BleActiveStateManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var logArea: TextView
    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var bleActiveStateManager: BleActiveStateManager

    // bluetoothAdapterの定義
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
        // Bluetooth有効確認クラス
        bleActiveStateManager = BleActiveStateManager(this, bluetoothAdapter)

        // Bluetoothが有効な状態かチェック
        bleActiveStateManager.checking()

        // パーミッションの状態を監視
        lifecycleScope.launch {
            bleActiveStateManager.permissionState.collect { state ->
                when (state) {
                    is BleActiveStateManager.BluetoothState.Initial -> {
                        Toast.makeText(this@MainActivity, "許可された", Toast.LENGTH_LONG).show()
                    }

                    is BleActiveStateManager.BluetoothState.Active -> {
                        Toast.makeText(this@MainActivity, "許可された", Toast.LENGTH_LONG).show()
                    }

                    is BleActiveStateManager.BluetoothState.NotSupport -> {
                        Toast.makeText(this@MainActivity, "許可された", Toast.LENGTH_LONG).show()
                    }

                    is BleActiveStateManager.BluetoothState.Denied -> {
                        Toast.makeText(this@MainActivity, "否認された", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        setUpUI()
    }

    private fun setUpUI() {
        // ログ表示ビュー
        logArea = findViewById(R.id.log)
        val connectButton: Button = findViewById(R.id.connect_button)
        val disConnectButton: Button = findViewById(R.id.disconnect_button)
        val scanButton: Button = findViewById(R.id.scan_button)
        // ペリフェラルとのスキャン処理
        scanButton.setOnClickListener { }
        // ペリフェラルとの接続処理
        connectButton.setOnClickListener { }
        // ペリフェラルとの切断処理
        disConnectButton.setOnClickListener { }
    }
}