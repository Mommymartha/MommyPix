package com.project.mommypix.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Optional Device Administrator receiver.
 * When enabled by the user, Android prevents direct uninstallation of the application
 * until the administrator privilege is explicitly revoked in system settings.
 * All normal app operations continue to function identically with or without this privilege.
 */
class MommyPixDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Administrator enabled: Uninstall protection is ACTIVE.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.i(TAG, "Device Administrator disable requested by user.")
        return "Deactivating Device Admin will remove uninstall protection for MommyPix."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Administrator disabled: Uninstall protection is INACTIVE.")
    }

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }
}

