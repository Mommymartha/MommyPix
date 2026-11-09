package com.project.mommypix.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.project.mommypix.MommyPixApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object WallpaperScheduler {

    const val WORK_NAME = "mommypix_auto_wallpaper_work"
    private const val ALARM_REQUEST_CODE = 4401
    private const val TAG = "WallpaperScheduler"

    /**
     * Schedules periodic wallpaper changes.
     * Uses AlarmManager with setExactAndAllowWhileIdle for exact intervals (e.g. 1 min, 5 min, etc.)
     * and WorkManager as an auxiliary fallback.
     */
    fun scheduleWallpaperWork(
        context: Context,
        intervalMinutes: Long,
        isWifiOnly: Boolean
    ) {
        val safeIntervalMinutes = intervalMinutes.coerceAtLeast(1L)
        Log.i(TAG, "Scheduling wallpaper work for interval: $safeIntervalMinutes minutes")

        // 1. Schedule via AlarmManager (supports 1 min, 2 min, etc., even in Doze mode)
        scheduleAlarm(context, safeIntervalMinutes)

        // 2. Aux WorkManager fallback (only if >= 15 minutes)
        if (safeIntervalMinutes >= 15L) {
            try {
                val networkType = if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                    safeIntervalMinutes, TimeUnit.MINUTES,
                    5L, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag(WORK_NAME)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager schedule fallback error", e)
            }
        } else {
            // For intervals < 15m, cancel WorkManager so only AlarmManager triggers
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    /**
     * Schedules the next exact alarm tick based on user's current preferences.
     */
    fun scheduleNextAlarm(context: Context) {
        val app = context.applicationContext as? MommyPixApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                if (prefs.isAutoWallpaperEnabled) {
                    scheduleAlarm(context, prefs.effectiveIntervalMinutes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in scheduleNextAlarm", e)
            }
        }
    }

    private fun scheduleAlarm(context: Context, intervalMinutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            action = WallpaperAlarmReceiver.ACTION_ALARM
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)

        try {
            // 1. Try setAlarmClock: Highest priority alarm on Android.
            // Bypasses Doze mode throttling and background process restrictions even when
            // the app is swiped away from recent tabs or screen is off.
            val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            Log.i(TAG, "High-priority AlarmClock armed for ${Date(triggerAtMillis)}")
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock not available, falling back to setExactAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.i(TAG, "Alarm successfully armed for ${Date(triggerAtMillis)}")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to schedule exact alarm", e2)
            }
        }
    }

    fun cancelWallpaperWork(context: Context) {
        // Cancel Alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            action = WallpaperAlarmReceiver.ACTION_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        // Cancel WorkManager
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "All background wallpaper tasks and alarms canceled.")
    }

    fun getWorkInfoFlow(context: Context): Flow<WorkInfo?> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { list -> list.firstOrNull() }
    }

    fun formatEstimatedNextUpdate(
        intervalMinutes: Long,
        lastTimestamp: Long,
        isRandom: Boolean = false,
        randomMin: Long = 5L,
        randomMax: Long = 30L
    ): String {
        if (isRandom) {
            val rangeLabel = "Random ($randomMin - $randomMax min)"
            if (lastTimestamp <= 0L) {
                return "$rangeLabel • Armed in background"
            }
            val nextTimeMs = lastTimestamp + (intervalMinutes.coerceAtLeast(1L) * 60 * 1000L)
            val sdf = SimpleDateFormat("HH:mm:ss, MMM dd", Locale.getDefault())
            return "$rangeLabel • Next update ~${sdf.format(Date(nextTimeMs))}"
        }

        val mins = intervalMinutes.coerceAtLeast(1L)
        val label = when {
            mins == 1L -> "1 minute"
            mins < 60 -> "$mins minutes"
            mins % 60L == 0L -> "${mins / 60} hours"
            else -> "${mins / 60}h ${mins % 60}m"
        }

        if (lastTimestamp <= 0L) {
            return "Scheduled every ~$label (Active in background)"
        }
        val nextTimeMs = lastTimestamp + (mins * 60 * 1000L)
        val sdf = SimpleDateFormat("HH:mm:ss, MMM dd", Locale.getDefault())
        return "Every ~$label • Next update ~${sdf.format(Date(nextTimeMs))}"
    }
}
