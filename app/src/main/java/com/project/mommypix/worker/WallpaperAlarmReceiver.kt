package com.project.mommypix.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.preferences.BothScreenMode
import com.project.mommypix.data.preferences.WallpaperTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WallpaperAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "WallpaperAlarmReceiver alarm fired!")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MommyPix:WallpaperAlarmWakeLock"
        )?.apply {
            acquire(90 * 1000L) // 90 seconds max safety timeout
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? MommyPixApp ?: return@launch
                val repository = app.repository
                val prefsRepository = app.preferencesRepository

                if (!repository.hasCredentials()) {
                    Log.w(TAG, "Credentials not configured; skipping automatic wallpaper change.")
                    return@launch
                }

                val userPrefs = prefsRepository.userPreferencesFlow.first()
                if (!userPrefs.isAutoWallpaperEnabled) {
                    Log.i(TAG, "Auto wallpaper is disabled.")
                    return@launch
                }

                Log.i(TAG, "Executing automatic wallpaper update for target: ${userPrefs.wallpaperTarget}")
                when (userPrefs.wallpaperTarget) {
                    WallpaperTarget.HOME -> {
                        val post = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                        repository.applyPostAsWallpaper(post, WallpaperTarget.HOME).getOrThrow()
                    }
                    WallpaperTarget.LOCK -> {
                        val post = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                        repository.applyPostAsWallpaper(post, WallpaperTarget.LOCK).getOrThrow()
                    }
                    WallpaperTarget.BOTH -> {
                        if (userPrefs.bothScreenMode == BothScreenMode.SAME_IMAGE) {
                            val post = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                            repository.applyPostAsWallpaper(post, WallpaperTarget.BOTH).getOrThrow()
                        } else {
                            val homePost = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                            val lockPost = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                            repository.applyDualDifferentWallpapers(homePost, lockPost).getOrThrow()
                        }
                    }
                }

                prefsRepository.setLastAutoUpdateTimestamp(System.currentTimeMillis())
                NotificationHelper.showWallpaperChangedNotification(context, userPrefs.wallpaperTarget)
                Log.i(TAG, "Automatic wallpaper updated successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during alarm-triggered wallpaper change", e)
            } finally {
                // Schedule the next alarm tick in the chain
                try {
                    WallpaperScheduler.scheduleNextAlarm(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to schedule next alarm", e)
                }

                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing WakeLock", e)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
        const val ACTION_ALARM = "com.project.mommypix.ACTION_UPDATE_WALLPAPER_ALARM"
    }
}

