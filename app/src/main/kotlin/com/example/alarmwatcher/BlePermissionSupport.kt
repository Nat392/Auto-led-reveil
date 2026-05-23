package com.example.alarmwatcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object BlePermissionSupport {
    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
}