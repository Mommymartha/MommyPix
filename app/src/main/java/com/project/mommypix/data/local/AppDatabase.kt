package com.project.mommypix.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class AppDatabase(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _favoritesFlow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    val favoritesFlow: StateFlow<List<FavoriteEntity>> = _favoritesFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow<List<DownloadHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<DownloadHistoryItem>> = _historyFlow.asStateFlow()

    init {
        // Initial load
        refreshFavorites()
        refreshHistory()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FAVORITES (
                $COL_ID INTEGER PRIMARY KEY,
                $COL_FILE_URL TEXT NOT NULL,
                $COL_PREVIEW_URL TEXT NOT NULL,
                $COL_SAMPLE_URL TEXT,
                $COL_WIDTH INTEGER NOT NULL,
                $COL_HEIGHT INTEGER NOT NULL,
                $COL_RATING TEXT NOT NULL,
                $COL_TAGS TEXT NOT NULL,
                $COL_SAVED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY,
                $COL_LOCAL_PATH TEXT NOT NULL,
                $COL_FILE_URL TEXT NOT NULL,
                $COL_PREVIEW_URL TEXT NOT NULL,
                $COL_WIDTH INTEGER NOT NULL,
                $COL_HEIGHT INTEGER NOT NULL,
                $COL_RATING TEXT NOT NULL,
                $COL_TAGS TEXT NOT NULL,
                $COL_DOWNLOADED_AT INTEGER NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    // --- Favorites Operations ---

    suspend fun addFavorite(entity: FavoriteEntity) = withContext(ioDispatcher) {
        val values = ContentValues().apply {
            put(COL_ID, entity.id)
            put(COL_FILE_URL, entity.fileUrl)
            put(COL_PREVIEW_URL, entity.previewUrl)
            put(COL_SAMPLE_URL, entity.sampleUrl)
            put(COL_WIDTH, entity.width)
            put(COL_HEIGHT, entity.height)
            put(COL_RATING, entity.rating)
            put(COL_TAGS, entity.tags)
            put(COL_SAVED_AT, entity.savedAt)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_FAVORITES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshFavorites()
    }

    suspend fun removeFavorite(id: Long) = withContext(ioDispatcher) {
        writableDatabase.delete(TABLE_FAVORITES, "$COL_ID = ?", arrayOf(id.toString()))
        refreshFavorites()
    }

    suspend fun isFavorite(id: Long): Boolean = withContext(ioDispatcher) {
        val cursor = readableDatabase.query(
            TABLE_FAVORITES,
            arrayOf(COL_ID),
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        exists
    }

    private fun refreshFavorites() {
        val list = mutableListOf<FavoriteEntity>()
        val cursor = readableDatabase.query(
            TABLE_FAVORITES,
            null, null, null, null, null,
            "$COL_SAVED_AT DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val fileUrlIdx = c.getColumnIndexOrThrow(COL_FILE_URL)
            val previewUrlIdx = c.getColumnIndexOrThrow(COL_PREVIEW_URL)
            val sampleUrlIdx = c.getColumnIndexOrThrow(COL_SAMPLE_URL)
            val widthIdx = c.getColumnIndexOrThrow(COL_WIDTH)
            val heightIdx = c.getColumnIndexOrThrow(COL_HEIGHT)
            val ratingIdx = c.getColumnIndexOrThrow(COL_RATING)
            val tagsIdx = c.getColumnIndexOrThrow(COL_TAGS)
            val savedAtIdx = c.getColumnIndexOrThrow(COL_SAVED_AT)

            while (c.moveToNext()) {
                list.add(
                    FavoriteEntity(
                        id = c.getLong(idIdx),
                        fileUrl = c.getString(fileUrlIdx),
                        previewUrl = c.getString(previewUrlIdx),
                        sampleUrl = c.getString(sampleUrlIdx),
                        width = c.getInt(widthIdx),
                        height = c.getInt(heightIdx),
                        rating = c.getString(ratingIdx),
                        tags = c.getString(tagsIdx),
                        savedAt = c.getLong(savedAtIdx)
                    )
                )
            }
        }
        _favoritesFlow.value = list
    }

    // --- History Operations ---

    suspend fun addDownloadHistory(item: DownloadHistoryItem) = withContext(ioDispatcher) {
        val values = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_LOCAL_PATH, item.localPath)
            put(COL_FILE_URL, item.fileUrl)
            put(COL_PREVIEW_URL, item.previewUrl)
            put(COL_WIDTH, item.width)
            put(COL_HEIGHT, item.height)
            put(COL_RATING, item.rating)
            put(COL_TAGS, item.tags)
            put(COL_DOWNLOADED_AT, item.downloadedAt)
            put(COL_FILE_SIZE, item.fileSizeBytes)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_HISTORY,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshHistory()
    }

    suspend fun deleteDownloadHistory(id: Long): Boolean = withContext(ioDispatcher) {
        // Query local path first to delete file
        val cursor = readableDatabase.query(
            TABLE_HISTORY,
            arrayOf(COL_LOCAL_PATH),
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        var localPath: String? = null
        if (cursor.moveToFirst()) {
            localPath = cursor.getString(0)
        }
        cursor.close()

        localPath?.let {
            val file = File(it)
            if (file.exists()) file.delete()
        }

        val rows = writableDatabase.delete(TABLE_HISTORY, "$COL_ID = ?", arrayOf(id.toString()))
        refreshHistory()
        rows > 0
    }

    suspend fun clearDownloadHistory() = withContext(ioDispatcher) {
        val cursor = readableDatabase.query(
            TABLE_HISTORY,
            arrayOf(COL_LOCAL_PATH),
            null, null, null, null, null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0)
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
        writableDatabase.delete(TABLE_HISTORY, null, null)
        refreshHistory()
    }

    suspend fun getStorageUsageBytes(): Long = withContext(ioDispatcher) {
        var total = 0L
        val cursor = readableDatabase.query(
            TABLE_HISTORY,
            arrayOf("SUM($COL_FILE_SIZE)"),
            null, null, null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                total = c.getLong(0)
            }
        }
        total
    }

    private fun refreshHistory() {
        val list = mutableListOf<DownloadHistoryItem>()
        val cursor = readableDatabase.query(
            TABLE_HISTORY,
            null, null, null, null, null,
            "$COL_DOWNLOADED_AT DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val pathIdx = c.getColumnIndexOrThrow(COL_LOCAL_PATH)
            val fileUrlIdx = c.getColumnIndexOrThrow(COL_FILE_URL)
            val previewUrlIdx = c.getColumnIndexOrThrow(COL_PREVIEW_URL)
            val widthIdx = c.getColumnIndexOrThrow(COL_WIDTH)
            val heightIdx = c.getColumnIndexOrThrow(COL_HEIGHT)
            val ratingIdx = c.getColumnIndexOrThrow(COL_RATING)
            val tagsIdx = c.getColumnIndexOrThrow(COL_TAGS)
            val downloadedAtIdx = c.getColumnIndexOrThrow(COL_DOWNLOADED_AT)
            val sizeIdx = c.getColumnIndexOrThrow(COL_FILE_SIZE)

            while (c.moveToNext()) {
                list.add(
                    DownloadHistoryItem(
                        id = c.getLong(idIdx),
                        localPath = c.getString(pathIdx),
                        fileUrl = c.getString(fileUrlIdx),
                        previewUrl = c.getString(previewUrlIdx),
                        width = c.getInt(widthIdx),
                        height = c.getInt(heightIdx),
                        rating = c.getString(ratingIdx),
                        tags = c.getString(tagsIdx),
                        downloadedAt = c.getLong(downloadedAtIdx),
                        fileSizeBytes = c.getLong(sizeIdx)
                    )
                )
            }
        }
        _historyFlow.value = list
    }

    companion object {
        private const val DATABASE_NAME = "mommypix.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_FAVORITES = "favorites"
        private const val TABLE_HISTORY = "download_history"

        private const val COL_ID = "id"
        private const val COL_FILE_URL = "file_url"
        private const val COL_PREVIEW_URL = "preview_url"
        private const val COL_SAMPLE_URL = "sample_url"
        private const val COL_WIDTH = "width"
        private const val COL_HEIGHT = "height"
        private const val COL_RATING = "rating"
        private const val COL_TAGS = "tags"
        private const val COL_SAVED_AT = "saved_at"

        private const val COL_LOCAL_PATH = "local_path"
        private const val COL_DOWNLOADED_AT = "downloaded_at"
        private const val COL_FILE_SIZE = "file_size"
    }
}

