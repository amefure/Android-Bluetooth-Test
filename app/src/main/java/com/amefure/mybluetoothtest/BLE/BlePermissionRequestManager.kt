package com.amefure.mybluetoothtest.BLE

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/** Bluetoothを使用するためのパーミッションを確認&リクエストするクラス */
class BlePermissionRequestManager(
    private val activity: ComponentActivity
) {

    /** パーミッションリクエスト */
    public fun request() {
        // 全ての権限がマニフェスト内で承認済みかチェック
        if (!permissionCheck) {
            // リクエストを投げたいパーミションをまとめる
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            // リクエスト送信 →　onRequestPermissionsResult
            launcher.launch(permissions)
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
                Toast.makeText(activity, "許可された", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, "否認された", Toast.LENGTH_LONG).show()
            }
        }
}