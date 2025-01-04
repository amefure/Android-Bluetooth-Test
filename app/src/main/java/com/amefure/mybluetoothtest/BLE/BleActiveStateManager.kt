package com.amefure.mybluetoothtest.BLE

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 *  Bluetoothを使用するためのパーミッションを確認&リクエストするクラス
 *  https://appdev-room.com/android-bluetooth-permission
 */
class BleActiveStateManager(
    private val activity: ComponentActivity,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    /** パーミッションの状態を保持する StateFlow */
    private val _permissionState = MutableStateFlow<BluetoothState>(BluetoothState.Initial)
    val permissionState: StateFlow<BluetoothState> = _permissionState

    /** Bluetoothサポートしているかのチェック */
    private val checkSupport: Boolean
        get() = bluetoothAdapter?.isEnabled ?: false

    /** Bluetooth有効状態チェック開始 */
    public fun checking() {
        // Bluetooth非サポート
        if (!checkSupport) {
            _permissionState.value = BluetoothState.NotSupport
            return
        }
        // 全ての権限がマニフェスト内で承認済みかチェック
        if (!permissionCheck) {
            // リクエストを投げたいパーミションをまとめる
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            // リクエスト送信 → onRequestPermissionsResultコールバック
            launcher.launch(permissions)
        } else {
            // 有効
            _permissionState.value = BluetoothState.Active
        }
    }

    /** リクエスト送信前にパーミッションが定義されているかチェックする */
    private val permissionCheck: Boolean
        get()  {
            val result = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            return  result
        }

    /** 許可ダイアログの表示と結果の処理を実装するランチャー */
    private var launcher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val location = it[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val connect = it[Manifest.permission.BLUETOOTH_CONNECT] ?: false
            val scan = it[Manifest.permission.BLUETOOTH_SCAN] ?: false
            if (location && connect && scan) {
                // 有効
                _permissionState.value = BluetoothState.Active
            } else {
                // 権限否認
                _permissionState.value = BluetoothState.Denied
            }
        }

    /** パーミッションの状態を表す sealed class */
    sealed class BluetoothState {
        /** 初期状態 */
        object Initial : BluetoothState()
        /** 有効 */
        object Active : BluetoothState()
        /** 初期状態 */
        object NotSupport : BluetoothState()
        /** 初期状態 */
        object Denied : BluetoothState()
    }
}