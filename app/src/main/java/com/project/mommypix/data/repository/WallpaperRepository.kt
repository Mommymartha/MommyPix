package com.project.mommypix.data.repository

import android.util.Log
import com.project.mommypix.data.api.ApiClient
import com.project.mommypix.data.api.Rule34Api
import com.project.mommypix.data.downloader.ImageDownloader
import com.project.mommypix.data.local.AppDatabase
import com.project.mommypix.data.local.DownloadHistoryItem
import com.project.mommypix.data.local.FavoriteEntity
import com.project.mommypix.data.preferences.UserPreferencesRepository
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.domain.model.Post
import com.project.mommypix.security.ContentFilter
import com.project.mommypix.security.CredentialStorage
import com.project.mommypix.wallpaper.WallpaperManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class WallpaperRepository(
    private val api: Rule34Api = ApiClient.rule34Api,
    private val credentialStorage: CredentialStorage,
    private val database: AppDatabase,
    private val downloader: ImageDownloader,
    private val wallpaperManagerHelper: WallpaperManagerHelper,
    private val preferencesRepository: UserPreferencesRepository? = null
) {

    // In-memory cache of recent post IDs to avoid immediate duplicates
    private val seenPostIds = mutableSetOf<Long>()

    // In-memory cache of active posts so clicking a preview immediately finds it
    private val recentPostsCache = LinkedHashMap<Long, Post>(100, 0.75f, true)

    // Buffer of fetched valid posts to provide smooth "Next Image" transitions
    private val postBuffer = ArrayDeque<Post>()

    fun getCachedPost(postId: Long): Post? = recentPostsCache[postId]

    fun cachePost(post: Post) {
        recentPostsCache[post.id] = post
    }

    fun clearBuffer() {
        postBuffer.clear()
        seenPostIds.clear()
    }

    /**
     * Checks if credentials are provided.
     */
    fun hasCredentials(): Boolean = credentialStorage.hasCredentials()

    /**
     * Tests API credentials and connectivity.
     * Returns true if request succeeds, or throws an informative exception.
     */
    suspend fun testApiConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = credentialStorage.getUserId()
            val apiKey = credentialStorage.getApiKey()

            if (userId.isNullOrBlank() || apiKey.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("API User ID and API Key must both be entered")
                )
            }

            val response = api.getPosts(
                limit = 1,
                tags = ContentFilter.buildSafeTagQuery("sort:random"),
                apiKey = apiKey,
                userId = userId
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Server responded with HTTP ${response.code()}: ${response.message()}")
                )
            }

            val body = response.body()
            if (body == null) {
                return@withContext Result.failure(Exception("Received empty response from server"))
            }

            Result.success("Connection successful! Authenticated with Rule34 API.")
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches a random valid post from Rule34.
     * Respects user's Included tags and Blocked tags (blacklist),
     * and strictly rejects prohibited illegal content and unsupported video formats.
     */
    suspend fun fetchRandomPost(forceRefresh: Boolean = false): Result<Post> = withContext(Dispatchers.IO) {
        try {
            if (!hasCredentials()) {
                return@withContext Result.failure(
                    IllegalStateException("Rule34 API credentials not configured. Please enter them in Settings.")
                )
            }

            if (!forceRefresh && postBuffer.isNotEmpty()) {
                val cached = postBuffer.removeFirst()
                val isFav = database.isFavorite(cached.id)
                return@withContext Result.success(cached.copy(isFavorite = isFav))
            }

            val userId = credentialStorage.getUserId()
            val apiKey = credentialStorage.getApiKey()

            // Read user tag preferences
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val includedTags = userPrefs?.includedTags.orEmpty()
            val blockedTags = userPrefs?.blockedTags.orEmpty()

            // Construct API search query with included and blocked tags
            val queryParts = mutableListOf<String>()
            queryParts.add("sort:random")

            includedTags.forEach { tag ->
                val clean = tag.trim().lowercase().replace(' ', '_')
                if (clean.isNotBlank()) queryParts.add(clean)
            }

            blockedTags.forEach { tag ->
                val clean = tag.trim().lowercase().replace(' ', '_')
                if (clean.isNotBlank()) queryParts.add("-$clean")
            }

            val safeQuery = ContentFilter.buildSafeTagQuery(queryParts.joinToString(" "))

            // Request a small batch (e.g. 10) to minimize network roundtrips and filter locally
            val response = api.getPosts(
                limit = 10,
                tags = safeQuery,
                apiKey = apiKey,
                userId = userId
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to fetch posts: HTTP ${response.code()} ${response.message()}")
                )
            }

            val dtoList = response.body().orEmpty()
            if (dtoList.isEmpty()) {
                return@withContext Result.failure(
                    Exception("No posts matched your tag filters. Try removing some included tags.")
                )
            }

            val blockedLower = blockedTags.map { it.lowercase().replace(' ', '_') }.toSet()
            val includedLower = includedTags.map { it.lowercase().replace(' ', '_') }.toSet()

            val validPosts = dtoList.mapNotNull { it.toDomain() }
                .filter { post ->
                    val postTagsLower = post.tags.map { it.lowercase() }.toSet()

                    // 1. Mandatory Legal Content Filter
                    val isLegal = ContentFilter.isPostPermitted(post.tags.joinToString(" "))

                    // 2. Only image formats, no video
                    val isImage = post.isSupportedImage

                    // 3. User blacklist filter: Must NOT contain any blocked tag
                    val containsBlocked = postTagsLower.any { blockedLower.contains(it) }

                    // 4. User whitelist filter: If user specified included tags, post should match
                    val matchesIncluded = includedLower.isEmpty() || postTagsLower.any { includedLower.contains(it) }

                    isLegal && isImage && !containsBlocked && matchesIncluded
                }

            if (validPosts.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Fetched posts were filtered out by your blocked tags or contained unsupported formats. Shuffling again...")
                )
            }

            for (p in validPosts) {
                recentPostsCache[p.id] = p
                if (!seenPostIds.contains(p.id)) {
                    postBuffer.add(p)
                }
            }

            // If all were seen, re-add to buffer to avoid infinite starvation
            if (postBuffer.isEmpty()) {
                postBuffer.addAll(validPosts)
            }

            val chosen = postBuffer.removeFirst()
            recentPostsCache[chosen.id] = chosen
            seenPostIds.add(chosen.id)
            if (seenPostIds.size > 200) {
                seenPostIds.clear()
            }

            val isFav = database.isFavorite(chosen.id)
            Result.success(chosen.copy(isFavorite = isFav))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching random post", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads and applies wallpaper according to the user's target settings.
     */
    suspend fun applyPostAsWallpaper(
        post: Post,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val downloadResult = downloader.downloadImage(post.fileUrl, post.id)
            val file = downloadResult.getOrElse { return@withContext Result.failure(it) }

            val applyResult = wallpaperManagerHelper.applyWallpaper(file, target)
            if (applyResult.isFailure) {
                return@withContext applyResult
            }

            database.addDownloadHistory(
                DownloadHistoryItem(
                    id = post.id,
                    localPath = file.absolutePath,
                    fileUrl = post.fileUrl,
                    previewUrl = post.previewUrl,
                    width = post.width,
                    height = post.height,
                    rating = post.rating,
                    tags = post.tags.joinToString(" "),
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = file.length()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper for post ${post.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Handles setting dual different wallpapers for Home and Lock screens.
     */
    suspend fun applyDualDifferentWallpapers(
        homePost: Post,
        lockPost: Post
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val homeDownload = downloader.downloadImage(homePost.fileUrl, homePost.id)
            val homeFile = homeDownload.getOrElse { return@withContext Result.failure(it) }

            val lockDownload = downloader.downloadImage(lockPost.fileUrl, lockPost.id)
            val lockFile = lockDownload.getOrElse { return@withContext Result.failure(it) }

            val applyResult = wallpaperManagerHelper.applyDualWallpapers(homeFile, lockFile)
            if (applyResult.isFailure) {
                return@withContext applyResult
            }

            database.addDownloadHistory(
                DownloadHistoryItem(
                    id = homePost.id,
                    localPath = homeFile.absolutePath,
                    fileUrl = homePost.fileUrl,
                    previewUrl = homePost.previewUrl,
                    width = homePost.width,
                    height = homePost.height,
                    rating = homePost.rating,
                    tags = homePost.tags.joinToString(" "),
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = homeFile.length()
                )
            )

            database.addDownloadHistory(
                DownloadHistoryItem(
                    id = lockPost.id,
                    localPath = lockFile.absolutePath,
                    fileUrl = lockPost.fileUrl,
                    previewUrl = lockPost.previewUrl,
                    width = lockPost.width,
                    height = lockPost.height,
                    rating = lockPost.rating,
                    tags = lockPost.tags.joinToString(" "),
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = lockFile.length()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dual wallpapers", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads an image and saves it to history.
     */
    suspend fun downloadAndSaveImage(
        post: Post,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadResult = downloader.downloadImage(post.fileUrl, post.id, onProgress)
        val file = downloadResult.getOrElse { return@withContext Result.failure(it) }

        database.addDownloadHistory(
            DownloadHistoryItem(
                id = post.id,
                localPath = file.absolutePath,
                fileUrl = post.fileUrl,
                previewUrl = post.previewUrl,
                width = post.width,
                height = post.height,
                rating = post.rating,
                tags = post.tags.joinToString(" "),
                downloadedAt = System.currentTimeMillis(),
                fileSizeBytes = file.length()
            )
        )

        Result.success(file)
    }

    /**
     * Applies a downloaded historical file directly as wallpaper.
     */
    suspend fun applyHistoricalWallpaper(
        item: DownloadHistoryItem,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(item.localPath)
        if (!file.exists()) {
            return@withContext Result.failure(Exception("File no longer exists on disk"))
        }
        wallpaperManagerHelper.applyWallpaper(file, target)
    }

    // --- Favorites ---

    suspend fun toggleFavorite(post: Post): Boolean = withContext(Dispatchers.IO) {
        val isFav = database.isFavorite(post.id)
        if (isFav) {
            database.removeFavorite(post.id)
            false
        } else {
            database.addFavorite(
                FavoriteEntity(
                    id = post.id,
                    fileUrl = post.fileUrl,
                    previewUrl = post.previewUrl,
                    sampleUrl = post.sampleUrl,
                    width = post.width,
                    height = post.height,
                    rating = post.rating,
                    tags = post.tags.joinToString(" ")
                )
            )
            true
        }
    }

    fun observeFavorites(): Flow<List<Post>> {
        return database.favoritesFlow.map { list -> list.map { it.toDomain() } }
    }

    fun observeDownloadHistory(): Flow<List<DownloadHistoryItem>> {
        return database.historyFlow
    }

    suspend fun deleteDownloadHistory(id: Long): Boolean {
        return database.deleteDownloadHistory(id)
    }

    suspend fun clearDownloadHistory() {
        database.clearDownloadHistory()
    }

    suspend fun getStorageUsageBytes(): Long {
        return database.getStorageUsageBytes()
    }

    suspend fun clearCache(): Long {
        return downloader.clearAllFiles()
    }

    companion object {
        private const val TAG = "WallpaperRepository"
    }
}
