package com.project.mommypix.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.project.mommypix.data.preferences.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class WallpaperManagerHelper(private val context: Context) {

    private val wallpaperManager: WallpaperManager by lazy {
        WallpaperManager.getInstance(context)
    }

    private val operationMutex = Mutex()

    /**
     * Applies an image file to the designated target screen.
     * Prevents duplicate simultaneous wallpaper operations using a mutex.
     */
    suspend fun applyWallpaper(
        imageFile: File,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                if (!wallpaperManager.isWallpaperSupported) {
                    return@withContext Result.failure(
                        UnsupportedOperationException("Wallpapers are not supported on this device")
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !wallpaperManager.isSetWallpaperAllowed) {
                    return@withContext Result.failure(
                        SecurityException("Setting wallpaper is restricted by device policy")
                    )
                }

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Image file does not exist or is empty")
                    )
                }

                val whichFlag = when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }

                // Use stream directly to minimize memory footprint
                FileInputStream(imageFile).use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setStream(stream, null, true, whichFlag)
                    } else {
                        wallpaperManager.setStream(stream)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error applying wallpaper to $target", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Applies two distinct images to Home and Lock screens separately.
     */
    suspend fun applyDualWallpapers(
        homeImageFile: File,
        lockImageFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // Pre-N devices do not support separate lock screen wallpapers
                    FileInputStream(homeImageFile).use { stream ->
                        wallpaperManager.setStream(stream)
                    }
                    return@withContext Result.success(Unit)
                }

                if (!wallpaperManager.isWallpaperSupported) {
                    return@withContext Result.failure(
                        UnsupportedOperationException("Wallpapers are not supported on this device")
                    )
                }

                if (!wallpaperManager.isSetWallpaperAllowed) {
                    return@withContext Result.failure(
                        SecurityException("Setting wallpaper is restricted by device policy")
                    )
                }

                // 1. Set Home Screen wallpaper
                FileInputStream(homeImageFile).use { stream ->
                    wallpaperManager.setStream(stream, null, true, WallpaperManager.FLAG_SYSTEM)
                }

                // 2. Set Lock Screen wallpaper
                FileInputStream(lockImageFile).use { stream ->
                    wallpaperManager.setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error applying dual wallpapers", e)
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperManagerHelper"
    }
}

