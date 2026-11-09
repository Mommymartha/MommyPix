package com.project.mommypix.data.downloader

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.project.mommypix.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class ImageDownloader(private val context: Context) {

    private val wallpapersDir: File by lazy {
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    /**
     * Downloads an image from its URL to internal app storage.
     * Validates content type, checks maximum file size (50MB safeguard),
     * and confirms it is a valid decodable bitmap.
     */
    suspend fun downloadImage(
        url: String,
        postId: Long,
        onProgress: ((Int) -> Unit)? = null,
        exportToGallery: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                return@withContext Result.failure(IllegalArgumentException("Invalid URL: $url"))
            }

            // Determine file extension
            val cleanUrl = url.substringBefore("?")
            val extension = cleanUrl.substringAfterLast('.', "jpg").lowercase()
            if (extension !in listOf("jpg", "jpeg", "png", "webp")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unsupported image format: .$extension")
                )
            }

            val outputFile = File(wallpapersDir, "mommypix_${postId}_${System.currentTimeMillis()}.$extension")

            val referer = when {
                url.contains("rule34") -> "https://rule34.xxx/"
                url.contains("gelbooru") -> "https://gelbooru.com/"
                url.contains("danbooru") -> "https://danbooru.donmai.us/"
                url.contains("pornpics") -> "https://www.pornpics.com/"
                else -> "https://safebooru.org/"
            }

            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Referer", referer)
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to download image: HTTP ${response.code} ${response.message}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                Exception("Empty response body from server")
            )

            val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
            if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                return@withContext Result.failure(
                    Exception("Server returned a webpage ($contentType) instead of an image file")
                )
            }

            val contentLength = body.contentLength()
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                return@withContext Result.failure(
                    Exception("Image file size exceeds safe limit (${contentLength / (1024 * 1024)}MB > 50MB)")
                )
            }

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0 && onProgress != null) {
                            val percent = ((totalBytesRead * 100) / contentLength).toInt()
                            onProgress(percent.coerceIn(0, 100))
                        }
                    }
                    output.flush()
                }
            }

            // Validate that downloaded file is decodable
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outputFile.absolutePath, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                val headerSnippet = try {
                    FileInputStream(outputFile).use { input ->
                        val bytes = ByteArray(64)
                        val read = input.read(bytes)
                        if (read > 0) String(bytes, 0, read, Charsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() || it in "<>[]{}:;\"'/" } else ""
                    }
                } catch (e: Exception) { "" }
                outputFile.delete()
                return@withContext Result.failure(
                    Exception("Downloaded file is not a valid decodable image${if (headerSnippet.isNotBlank()) " (Preview: '$headerSnippet')" else ""}")
                )
            }

            // Export to device's public Gallery album only when explicitly enabled/requested
            if (exportToGallery) {
                exportToGallery(outputFile, postId)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download error for post $postId", e)
            Result.failure(e)
        }
    }

    /**
     * Saves a copy of the image to the device's public Pictures/MommyPix album so it appears in the user's Gallery.
     */
    fun exportToGallery(file: File, postId: Long): Uri? {
        return try {
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val filename = "MommyPix_${postId}_${System.currentTimeMillis()}.$extension"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MommyPix")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "Could not export image to gallery", e)
            null
        }
    }

    /**
     * Clear all cached downloaded wallpaper files.
     */
    suspend fun clearAllFiles(): Long = withContext(Dispatchers.IO) {
        var deletedCount = 0L
        wallpapersDir.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }
        deletedCount
    }

    companion object {
        private const val TAG = "ImageDownloader"
        private const val MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024L // 50MB
    }
}
