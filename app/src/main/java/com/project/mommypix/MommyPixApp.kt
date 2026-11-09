package com.project.mommypix

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.project.mommypix.data.api.ApiClient
import com.project.mommypix.data.downloader.ImageDownloader
import com.project.mommypix.data.local.AppDatabase
import com.project.mommypix.data.preferences.UserPreferencesRepository
import com.project.mommypix.data.repository.WallpaperRepository
import com.project.mommypix.security.CredentialStorage
import com.project.mommypix.wallpaper.WallpaperManagerHelper
import com.project.mommypix.worker.NotificationHelper
import com.project.mommypix.worker.WallpaperScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MommyPixApp : Application(), ImageLoaderFactory {

    lateinit var credentialStorage: CredentialStorage
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var downloader: ImageDownloader
        private set

    lateinit var wallpaperManagerHelper: WallpaperManagerHelper
        private set

    lateinit var repository: WallpaperRepository
        private set

    override fun onCreate() {
        super.onCreate()

        credentialStorage = CredentialStorage(this)
        preferencesRepository = UserPreferencesRepository(this)
        database = AppDatabase(this)
        downloader = ImageDownloader(this)
        wallpaperManagerHelper = WallpaperManagerHelper(this)

        repository = WallpaperRepository(
            api = ApiClient.rule34Api,
            credentialStorage = credentialStorage,
            database = database,
            downloader = downloader,
            wallpaperManagerHelper = wallpaperManagerHelper,
            preferencesRepository = preferencesRepository
        )

        // Create notification channel
        NotificationHelper.createNotificationChannel(this)

        // Ensure alarm is active if automatic wallpaper updates are enabled
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                if (prefs.isAutoWallpaperEnabled) {
                    Log.i("MommyPixApp", "Auto wallpaper is enabled, arming alarm for ${prefs.effectiveIntervalMinutes}m")
                    WallpaperScheduler.scheduleWallpaperWork(
                        this@MommyPixApp,
                        prefs.effectiveIntervalMinutes,
                        prefs.isWifiOnly
                    )
                }
            } catch (e: Exception) {
                Log.e("MommyPixApp", "Failed to check auto wallpaper schedule on app start", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(ApiClient.okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
