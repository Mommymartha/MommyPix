package com.project.mommypix.ui.screens.preview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.domain.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val post: Post? = null,
    val isLoading: Boolean = false,
    val isApplyingWallpaper: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class PreviewViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MommyPixApp
    private val repository = app.repository
    private val database = app.database

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    fun loadPost(postId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Check if it's currently loaded or in favorites or in history
            val cached = repository.getCachedPost(postId)
            if (cached != null) {
                val isFav = database.isFavorite(postId)
                _uiState.update { it.copy(isLoading = false, post = cached.copy(isFavorite = isFav)) }
                return@launch
            }

            val favs = database.favoritesFlow.value
            val fav = favs.find { it.id == postId }
            if (fav != null) {
                _uiState.update { it.copy(isLoading = false, post = fav.toDomain()) }
                return@launch
            }

            val history = database.historyFlow.value
            val hist = history.find { it.id == postId }
            if (hist != null) {
                val isFav = database.isFavorite(postId)
                _uiState.update { it.copy(isLoading = false, post = hist.toDomain(isFav)) }
                return@launch
            }

            // Fallback: fetch random post
            val result = repository.fetchRandomPost(forceRefresh = false)
            result.fold(
                onSuccess = { post ->
                    _uiState.update { it.copy(isLoading = false, post = post) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.localizedMessage)
                    }
                }
            )
        }
    }

    fun setPostDirect(post: Post) {
        _uiState.update { it.copy(post = post) }
    }

    fun fetchAnotherRandomPost() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.fetchRandomPost(forceRefresh = true)
            result.fold(
                onSuccess = { post ->
                    _uiState.update { it.copy(isLoading = false, post = post) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.localizedMessage)
                    }
                }
            )
        }
    }

    fun applyWallpaper(target: WallpaperTarget) {
        val post = _uiState.value.post ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true, errorMessage = null) }
            val result = repository.applyPostAsWallpaper(post, target)
            result.fold(
                onSuccess = {
                    val msg = when (target) {
                        WallpaperTarget.HOME -> "Applied to Home screen!"
                        WallpaperTarget.LOCK -> "Applied to Lock screen!"
                        WallpaperTarget.BOTH -> "Applied to Both screens!"
                    }
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, userMessage = msg)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isApplyingWallpaper = false,
                            errorMessage = "Failed to apply wallpaper: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun downloadImage() {
        val post = _uiState.value.post ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0, errorMessage = null) }
            val result = repository.downloadAndSaveImage(post) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 100,
                            userMessage = "Wallpaper #${post.id} saved to device!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            errorMessage = "Download failed: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun toggleFavorite() {
        val post = _uiState.value.post ?: return
        viewModelScope.launch {
            val newFav = repository.toggleFavorite(post)
            _uiState.update {
                it.copy(
                    post = post.copy(isFavorite = newFav),
                    userMessage = if (newFav) "Added to Favorites" else "Removed from Favorites"
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}

