package com.project.mommypix.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceAdminManager(private val context: Context) {

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val adminComponent = ComponentName(context, MommyPixDeviceAdminReceiver::class.java)

    val isDeviceAdminActive: Boolean
        get() = devicePolicyManager?.isAdminActive(adminComponent) == true

    fun createEnableAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enabling Device Admin prevents MommyPix from being accidentally or impulsively uninstalled."
            )
        }
    }

    fun createSecuritySettingsFallbackIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
    }

    fun deactivateAdmin(): Boolean {
        return try {
            if (isDeviceAdminActive) {
                devicePolicyManager?.removeActiveAdmin(adminComponent)
                Log.i(TAG, "Device Admin successfully deactivated.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating device admin", e)
            false
        }
    }

    companion object {
        private const val TAG = "DeviceAdminManager"
    }
}

