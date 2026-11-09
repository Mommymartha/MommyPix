package com.project.mommypix.data.repository

import android.util.Log
import com.project.mommypix.data.api.ApiClient
import com.project.mommypix.data.api.PostDto
import com.project.mommypix.data.api.Rule34Api
import com.project.mommypix.data.downloader.ImageDownloader
import com.project.mommypix.data.downloader.PornPicsScraper
import com.project.mommypix.data.local.AppDatabase
import com.project.mommypix.data.local.DownloadHistoryItem
import com.project.mommypix.data.local.FavoriteEntity
import com.project.mommypix.data.preferences.ImageSource
import com.project.mommypix.data.preferences.RatingFilter
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
import retrofit2.Response
import java.io.File

class WallpaperRepository(
    private val api: Rule34Api = ApiClient.rule34Api,
    private val credentialStorage: CredentialStorage,
    private val database: AppDatabase,
    private val downloader: ImageDownloader,
    private val wallpaperManagerHelper: WallpaperManagerHelper,
    private val preferencesRepository: UserPreferencesRepository? = null,
    private val pornPicsScraper: PornPicsScraper = PornPicsScraper()
) {

    // In-memory cache of recent post IDs to avoid immediate duplicates
    private val seenPostIds = mutableSetOf<Long>()

    // In-memory cache of active posts so clicking a preview immediately finds it
    private val recentPostsCache = LinkedHashMap<Long, Post>(100, 0.75f, true)

    // Buffer of fetched valid posts to provide smooth transitions
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
     * Checks if image fetching is possible.
     * Always returns true because zero-auth open sources (Safebooru, Konachan, Yande.re)
     * and default public Rule34 credentials are ready out-of-the-box.
     */
    fun hasCredentials(): Boolean = true

    fun hasCustomCredentials(): Boolean = credentialStorage.hasCustomCredentials()

    /**
     * Tests API connectivity.
     */
    suspend fun testApiConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val useCustom = userPrefs?.useCustomRule34Credentials ?: false
            val userId = credentialStorage.getEffectiveUserId(useCustom)
            val apiKey = credentialStorage.getEffectiveApiKey(useCustom)

            // Test Rule34 access
            val r34Response = api.getGelbooruPosts(
                url = "https://api.rule34.xxx/index.php",
                limit = 1,
                tags = ContentFilter.buildSafeTagQuery("order:random"),
                apiKey = apiKey,
                userId = userId
            )

            // Test Open Source access (Safebooru)
            val safebooruResponse = api.getGelbooruPosts(
                url = "https://safebooru.org/index.php",
                limit = 1,
                tags = ContentFilter.buildSafeTagQuery("order:random")
            )

            val r34Ok = r34Response.isSuccessful
            val safeOk = safebooruResponse.isSuccessful

            if (r34Ok || safeOk) {
                val details = buildString {
                    append("Connection successful! ")
                    if (r34Ok) append("Rule34 (Authenticated) OK. ")
                    if (safeOk) append("Safebooru (Public) OK. ")
                }
                Result.success(details)
            } else {
                Result.failure(Exception("Could not connect to image sources: HTTP ${r34Response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches a random valid post across enabled sources.
     * Respects user's Included tags and Blocked tags (blacklist),
     * and strictly rejects prohibited illegal content and unsupported formats.
     */
    suspend fun fetchRandomPost(forceRefresh: Boolean = false): Result<Post> = withContext(Dispatchers.IO) {
        try {
            if (forceRefresh) {
                postBuffer.clear()
            } else if (postBuffer.isNotEmpty()) {
                postBuffer.shuffle()
                val cached = postBuffer.removeFirst()
                val isFav = database.isFavorite(cached.id)
                return@withContext Result.success(cached.copy(isFavorite = isFav))
            }

            // Read user preferences
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val includedTags = userPrefs?.includedTags.orEmpty()
            val blockedTags = userPrefs?.blockedTags.orEmpty()
            val useCustomRule34 = userPrefs?.useCustomRule34Credentials ?: false
            val ratingFilter = userPrefs?.ratingFilter ?: RatingFilter.BOTH

            val enabledSourceIds = userPrefs?.enabledSources ?: ImageSource.DEFAULT_ENABLED
            val candidateSources = enabledSourceIds.mapNotNull { ImageSource.fromId(it) }
                .ifEmpty { ImageSource.entries }
                .shuffled()

            val blockedLower = blockedTags.map { it.lowercase().replace(' ', '_') }.toSet()
            val includedLower = includedTags.map { it.lowercase().replace(' ', '_') }.toSet()

            var lastError: Exception? = null

            // Iterate through randomized candidate sources until valid posts are retrieved
            for (source in candidateSources) {
                try {
                    // Special handling for PornPics web scraper
                    if (source == ImageSource.PORNPICS) {
                        if (ratingFilter == RatingFilter.SAFE_ONLY) continue
                        val ppPosts = pornPicsScraper.fetchRandomPosts(includedTags, blockedTags)
                        if (ppPosts.isNotEmpty()) {
                            val shuffledPP = ppPosts.shuffled()
                            for (p in shuffledPP) {
                                recentPostsCache[p.id] = p
                                if (!seenPostIds.contains(p.id)) {
                                    postBuffer.add(p)
                                }
                            }
                            if (postBuffer.isEmpty()) {
                                postBuffer.addAll(shuffledPP)
                            }
                            postBuffer.shuffle()
                            val chosen = postBuffer.removeFirst()
                            recentPostsCache[chosen.id] = chosen
                            seenPostIds.add(chosen.id)
                            val isFav = database.isFavorite(chosen.id)
                            return@withContext Result.success(chosen.copy(isFavorite = isFav))
                        }
                        continue
                    }

                    val dtoList = fetchFromSource(source, includedTags, blockedTags, useCustomRule34, ratingFilter)
                    if (dtoList.isEmpty()) continue

                    val validPosts = dtoList.mapNotNull { it.toDomain(source) }
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

                            // 5. Rating filter: Safe vs NSFW vs Both
                            val r = post.rating.lowercase()
                            val matchesRating = when (ratingFilter) {
                                RatingFilter.BOTH -> true
                                RatingFilter.SAFE_ONLY -> r == "s" || r == "safe" || r == "general" || r == "g"
                                RatingFilter.NSFW_ONLY -> r == "q" || r == "questionable" || r == "e" || r == "explicit"
                            }

                            isLegal && isImage && !containsBlocked && matchesIncluded && matchesRating
                        }

                    if (validPosts.isNotEmpty()) {
                        val shuffledPosts = validPosts.shuffled()
                        for (p in shuffledPosts) {
                            recentPostsCache[p.id] = p
                            if (!seenPostIds.contains(p.id)) {
                                postBuffer.add(p)
                            }
                        }

                        if (postBuffer.isEmpty()) {
                            postBuffer.addAll(shuffledPosts)
                        }

                        postBuffer.shuffle()
                        val chosen = postBuffer.removeFirst()
                        recentPostsCache[chosen.id] = chosen
                        seenPostIds.add(chosen.id)
                        if (seenPostIds.size > 300) {
                            seenPostIds.clear()
                        }

                        val isFav = database.isFavorite(chosen.id)
                        return@withContext Result.success(chosen.copy(isFavorite = isFav))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Source ${source.displayName} query failed, trying next source", e)
                    lastError = e
                }
            }

            // Fallback error if all sources failed
            Result.failure(
                lastError ?: Exception("No wallpapers found matching your filters across enabled sources.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching random post", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchFromSource(
        source: ImageSource,
        includedTags: Set<String>,
        blockedTags: Set<String>,
        useCustomRule34: Boolean,
        ratingFilter: RatingFilter
    ): List<PostDto> {
        val queryParts = mutableListOf<String>()

        if (source == ImageSource.PORNPICS) {
            return emptyList()
        }

        when (source) {
            ImageSource.SAFEBOORU, ImageSource.RULE34, ImageSource.GELBOORU, ImageSource.TBIB -> {
                queryParts.add("order:random")
                when (ratingFilter) {
                    RatingFilter.SAFE_ONLY -> queryParts.add("rating:safe")
                    RatingFilter.NSFW_ONLY -> queryParts.add("-rating:safe")
                    RatingFilter.BOTH -> { /* no rating filter token */ }
                }
            }
            ImageSource.KONACHAN, ImageSource.YANDE_RE -> {
                queryParts.add("order:random")
                when (ratingFilter) {
                    RatingFilter.SAFE_ONLY -> queryParts.add("rating:s")
                    RatingFilter.NSFW_ONLY -> queryParts.add("-rating:s")
                    RatingFilter.BOTH -> { /* no rating filter token */ }
                }
            }
            ImageSource.DANBOORU -> {
                queryParts.add("order:random")
                when (ratingFilter) {
                    RatingFilter.SAFE_ONLY -> queryParts.add("rating:g")
                    RatingFilter.NSFW_ONLY -> queryParts.add("-rating:g")
                    RatingFilter.BOTH -> { /* no rating filter token */ }
                }
            }
            ImageSource.PORNPICS -> {
                // PornPics content is retrieved via PornPicsScraper; no Booru query needed
            }
        }

        includedTags.forEach { tag ->
            val clean = tag.trim().lowercase().replace(' ', '_')
            if (clean.isNotBlank()) queryParts.add(clean)
        }

        blockedTags.forEach { tag ->
            val clean = tag.trim().lowercase().replace(' ', '_')
            if (clean.isNotBlank()) queryParts.add("-$clean")
        }

        val safeQuery = ContentFilter.buildSafeTagQuery(queryParts.joinToString(" "))

        val response: Response<List<PostDto>> = when (source) {
            ImageSource.SAFEBOORU -> {
                api.getGelbooruPosts(
                    url = "https://safebooru.org/index.php",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.GELBOORU -> {
                api.getGelbooruPosts(
                    url = "https://gelbooru.com/index.php",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.TBIB -> {
                api.getGelbooruPosts(
                    url = "https://tbib.org/index.php",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.KONACHAN -> {
                api.getMoebooruPosts(
                    url = "https://konachan.net/post.json",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.YANDE_RE -> {
                api.getMoebooruPosts(
                    url = "https://yande.re/post.json",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.DANBOORU -> {
                api.getMoebooruPosts(
                    url = "https://danbooru.donmai.us/posts.json",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.RULE34 -> {
                val userId = credentialStorage.getEffectiveUserId(useCustomRule34)
                val apiKey = credentialStorage.getEffectiveApiKey(useCustomRule34)
                api.getGelbooruPosts(
                    url = "https://api.rule34.xxx/index.php",
                    limit = 10,
                    tags = safeQuery,
                    apiKey = apiKey,
                    userId = userId
                )
            }
            ImageSource.PORNPICS -> return emptyList()
        }

        return if (response.isSuccessful) response.body().orEmpty() else emptyList()
    }

    /**
     * Downloads post image with multi-tier fallback:
     * 1. Primary fileUrl
     * 2. sampleUrl (if present and different)
     * 3. previewUrl (what user sees rendered on screen)
     */
    private suspend fun downloadPostImageWithFallback(
        post: Post,
        onProgress: ((Int) -> Unit)? = null,
        exportToGallery: Boolean = false
    ): Result<File> {
        // 1. Attempt primary fileUrl
        val primaryResult = downloader.downloadImage(post.fileUrl, post.id, onProgress, exportToGallery)
        if (primaryResult.isSuccess) {
            return primaryResult
        }

        Log.w(TAG, "Primary fileUrl download failed for #${post.id} (${post.fileUrl}): ${primaryResult.exceptionOrNull()?.message}. Attempting fallback...")

        // 2. Attempt sampleUrl if present and different
        if (!post.sampleUrl.isNullOrBlank() && post.sampleUrl != post.fileUrl) {
            val sampleResult = downloader.downloadImage(post.sampleUrl, post.id, onProgress, exportToGallery)
            if (sampleResult.isSuccess) {
                return sampleResult
            }
        }

        // 3. Fallback to previewUrl (what user sees on screen)
        if (post.previewUrl.isNotBlank() && post.previewUrl != post.fileUrl) {
            val previewResult = downloader.downloadImage(post.previewUrl, post.id, onProgress, exportToGallery)
            if (previewResult.isSuccess) {
                return previewResult
            }
        }

        return primaryResult
    }

    /**
     * Downloads and applies wallpaper according to the user's target settings.
     */
    suspend fun applyPostAsWallpaper(
        post: Post,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val saveToGallery = userPrefs?.saveToGallery ?: false
            val downloadResult = downloadPostImageWithFallback(post, exportToGallery = saveToGallery)
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
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val saveToGallery = userPrefs?.saveToGallery ?: false
            val homeDownload = downloadPostImageWithFallback(homePost, exportToGallery = saveToGallery)
            val homeFile = homeDownload.getOrElse { return@withContext Result.failure(it) }

            val lockDownload = downloadPostImageWithFallback(lockPost, exportToGallery = saveToGallery)
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
        val downloadResult = downloadPostImageWithFallback(post, onProgress, exportToGallery = true)
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
