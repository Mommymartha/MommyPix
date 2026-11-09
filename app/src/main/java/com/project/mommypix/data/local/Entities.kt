package com.project.mommypix.data.local

import com.project.mommypix.domain.model.Post
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FavoriteEntity(
    val id: Long,
    val fileUrl: String,
    val previewUrl: String,
    val sampleUrl: String?,
    val width: Int,
    val height: Int,
    val rating: String,
    val tags: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Post {
        val tagList = tags.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return Post(
            id = id,
            fileUrl = fileUrl,
            previewUrl = previewUrl,
            sampleUrl = sampleUrl,
            width = width,
            height = height,
            rating = rating,
            tags = tagList,
            isFavorite = true
        )
    }
}

data class DownloadHistoryItem(
    val id: Long,
    val localPath: String,
    val fileUrl: String,
    val previewUrl: String,
    val width: Int,
    val height: Int,
    val rating: String,
    val tags: String,
    val downloadedAt: Long,
    val fileSizeBytes: Long
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(downloadedAt))
        }

    val formattedSize: String
        get() {
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                String.format(Locale.US, "%.0f KB", kb)
            }
        }

    val existsOnDisk: Boolean
        get() = File(localPath).exists()

    fun toDomain(isFavorite: Boolean = false): Post {
        val tagList = tags.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return Post(
            id = id,
            fileUrl = fileUrl,
            previewUrl = previewUrl,
            sampleUrl = null,
            width = width,
            height = height,
            rating = rating,
            tags = tagList,
            isFavorite = isFavorite
        )
    }
}

