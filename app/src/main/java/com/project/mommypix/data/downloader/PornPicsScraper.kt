package com.project.mommypix.data.downloader

import android.util.Log
import com.project.mommypix.data.api.ApiClient
import com.project.mommypix.domain.model.Post
import com.project.mommypix.security.ContentFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import kotlin.random.Random

class PornPicsScraper(
    private val client: OkHttpClient = ApiClient.okHttpClient
) {

    private val thumbItemPattern = Pattern.compile(
        "<li[^>]*class=['\"][^'\"]*thumbwook[^'\"]*['\"][^>]*>.*?</li>",
        Pattern.DOTALL
    )

    private val imgSrcPattern = Pattern.compile(
        "(?:data-src|src)=['\"](https://cdni\\.pornpics\\.com/(?:460|1280)/[^'\"]+)['\"]"
    )

    private val dataGidPattern = Pattern.compile("data-gid=['\"](\\d+)['\"]")
    private val altPattern = Pattern.compile("alt=['\"]([^'\"]*)['\"]")

    /**
     * Scrapes random wallpapers from PornPics across diverse photo categories,
     * randomized search queries, and dynamic pagination offsets.
     */
    suspend fun fetchRandomPosts(
        includedTags: Set<String>,
        blockedTags: Set<String>
    ): List<Post> = withContext(Dispatchers.IO) {
        val blockedLower = blockedTags.map { it.lowercase().replace(' ', '-') }.toSet()
        val includedLower = includedTags.map { it.lowercase().replace(' ', '-') }.filter { it.isNotBlank() }

        // Pick a target keyword: user's included tag or random popular discovery category
        val targetKeyword = if (includedLower.isNotEmpty()) {
            includedLower.random()
        } else {
            POPULAR_DISCOVERY_CATEGORIES.random()
        }

        val randomOffset = Random.nextInt(0, 15) * 20
        val randomPage = Random.nextInt(1, 10)

        // Multiple dynamic candidate endpoints so every request gets a unique page
        val candidateUrls = listOf(
            "https://www.pornpics.com/search/recent/?q=$targetKeyword",
            "https://www.pornpics.com/search/?q=$targetKeyword&p=$randomPage",
            "https://www.pornpics.com/channels/$targetKeyword/",
            "https://www.pornpics.com/tags/$targetKeyword/",
            "https://www.pornpics.com/$targetKeyword/recent/?limit=50&offset=$randomOffset",
            "https://www.pornpics.com/recent/?limit=50&offset=$randomOffset"
        ).shuffled()

        var html = ""
        for (url in candidateUrls) {
            html = fetchHtml(url)
            if (html.isNotBlank() && html.contains("thumbwook")) {
                break
            }
        }

        if (html.isBlank()) {
            // Fallback to recent page
            html = fetchHtml("https://www.pornpics.com/recent/")
        }

        if (html.isBlank()) {
            return@withContext emptyList()
        }

        parseHtml(html, blockedLower, includedLower).shuffled()
    }

    private fun fetchHtml(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.pornpics.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string().orEmpty()
                } else {
                    Log.w(TAG, "PornPics request to $url returned HTTP ${response.code}")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching HTML from PornPics: ${e.message}")
            ""
        }
    }

    private fun parseHtml(
        html: String,
        blockedLower: Set<String>,
        includedLower: List<String>
    ): List<Post> {
        val posts = mutableListOf<Post>()
        val matcher = thumbItemPattern.matcher(html)

        while (matcher.find()) {
            val itemHtml = matcher.group()

            // 1. Extract image URL (supports both data-src and src)
            val srcMatcher = imgSrcPattern.matcher(itemHtml)
            if (!srcMatcher.find()) continue
            val rawUrl = srcMatcher.group(1)

            // Direct high-quality image URL from CDN (460px high density)
            val imageUrl = rawUrl

            // 2. Extract ID
            val gidMatcher = dataGidPattern.matcher(itemHtml)
            val rawId = if (gidMatcher.find()) {
                gidMatcher.group(1).toLongOrNull() ?: kotlin.math.abs(imageUrl.hashCode().toLong())
            } else {
                kotlin.math.abs(imageUrl.hashCode().toLong())
            }
            val safePostId = 90_000_000_000L + (rawId % 10_000_000_000L)

            // 3. Extract Alt Text / Title
            val altMatcher = altPattern.matcher(itemHtml)
            val title = if (altMatcher.find()) altMatcher.group(1).trim() else ""

            // 4. Content Safety Filter (Mandatory legal checks)
            if (!ContentFilter.isPostPermitted(title)) {
                continue
            }

            // 5. Parse words into tags
            val tagTokens = title.lowercase()
                .split("[^a-zA-Z0-9]+".toRegex())
                .filter { it.length > 2 }

            // 6. Blacklist check
            val containsBlocked = tagTokens.any { token -> blockedLower.contains(token) }
            if (containsBlocked) continue

            // 7. Whitelist check (if specified)
            if (includedLower.isNotEmpty()) {
                val matchesAny = includedLower.any { req ->
                    val cleanReq = req.replace('-', ' ')
                    title.contains(cleanReq, ignoreCase = true) || tagTokens.contains(req)
                }
                if (!matchesAny) continue
            }

            posts.add(
                Post(
                    id = safePostId,
                    fileUrl = imageUrl,
                    previewUrl = imageUrl,
                    sampleUrl = null,
                    width = 460,
                    height = 690,
                    rating = "explicit",
                    tags = tagTokens.ifEmpty { listOf("pornpics", "photo") },
                    sourceName = "PornPics"
                )
            )
        }

        return posts.shuffled()
    }

    companion object {
        private const val TAG = "PornPicsScraper"

        private val POPULAR_DISCOVERY_CATEGORIES = listOf(
            "babe", "glamour", "lingerie", "bikini", "milf", "brunette", "blonde", "redhead",
            "asian", "ebony", "mature", "stockings", "high-heels", "legs", "solo", "erotic",
            "cosplay", "sensual", "outdoor", "boobs", "ass", "pussy", "nude", "fitness",
            "thong", "curvy", "tattoos", "striptease", "bedroom", "shower", "beach"
        )
    }
}
