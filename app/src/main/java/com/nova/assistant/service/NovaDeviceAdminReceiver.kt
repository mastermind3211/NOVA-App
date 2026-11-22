package com.nova.assistant.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class NovaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    companion object {
        fun lockScreenNow(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, NovaDeviceAdminReceiver::class.java)
            return if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
                true
            } else {
                false
            }
        }
    }
}
