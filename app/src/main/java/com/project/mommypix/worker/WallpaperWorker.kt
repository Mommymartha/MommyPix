package com.project.mommypix.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.preferences.BothScreenMode
import com.project.mommypix.data.preferences.WallpaperTarget
import kotlinx.coroutines.flow.first

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting scheduled wallpaper change work...")

        val app = applicationContext as? MommyPixApp ?: return Result.failure()
        val repository = app.repository
        val prefsRepository = app.preferencesRepository

        if (!repository.hasCredentials()) {
            Log.w(TAG, "API credentials not configured; skipping automatic wallpaper change.")
            return Result.failure()
        }

        try {
            val userPrefs = prefsRepository.userPreferencesFlow.first()
            if (!userPrefs.isAutoWallpaperEnabled) {
                Log.i(TAG, "Automatic wallpaper change is disabled in settings.")
                return Result.success()
            }

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
                        // Different images for Home and Lock screens
                        val homePost = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                        val lockPost = repository.fetchRandomPost(forceRefresh = true).getOrThrow()
                        repository.applyDualDifferentWallpapers(homePost, lockPost).getOrThrow()
                    }
                }
            }

            prefsRepository.setLastAutoUpdateTimestamp(System.currentTimeMillis())
            NotificationHelper.showWallpaperChangedNotification(applicationContext, userPrefs.wallpaperTarget)
            Log.i(TAG, "Scheduled wallpaper update finished successfully.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Scheduled wallpaper update encountered an error", e)
            return if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperWorker"
    }
}
