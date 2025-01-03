package com.amefure.mybluetoothtest

import android.content.Context
import android.content.SharedPreferences

/** ローカル保存管理クラス */
class SharedPreferencesManager(private var context: Context) {
    private var sharedPref: SharedPreferences? = null

    companion object {
        /** デバイスアドレス格納 */
        const val ADDRESS_KEY = "ADDRESS_KEY"
    }

    /** 初期セットアップ */
    public fun setUp() {
        sharedPref = context.getSharedPreferences("com.example.preferences.preference_file_key", Context.MODE_PRIVATE)
    }

    /** 初期セットアップ */
    public fun save(key: String, str: String) {
        val editor = sharedPref?.edit()
        editor?.putString(key, str)?.apply()
    }

    /** 初期セットアップ */
    public fun fetch(key: String): String? {
        return sharedPref?.getString(key, null)
    }
}