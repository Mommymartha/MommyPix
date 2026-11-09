package com.project.mommypix.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.project.mommypix.MommyPixApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as? MommyPixApp ?: return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = app.preferencesRepository.userPreferencesFlow.first()
                    if (prefs.isAutoWallpaperEnabled) {
                        Log.i(TAG, "Rescheduling wallpaper alarm after reboot: ${prefs.effectiveIntervalMinutes}m")
                        WallpaperScheduler.scheduleWallpaperWork(
                            context,
                            prefs.effectiveIntervalMinutes,
                            prefs.isWifiOnly
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore wallpaper alarm on boot", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

