package com.project.mommypix.ui.screens.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.domain.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val favoritePosts: List<Post> = emptyList(),
    val isApplying: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MommyPixApp
    private val repository = app.repository

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeFavorites().collectLatest { posts ->
                _uiState.update { it.copy(favoritePosts = posts) }
            }
        }
    }

    fun removeFavorite(post: Post) {
        viewModelScope.launch {
            repository.toggleFavorite(post)
            _uiState.update {
                it.copy(userMessage = "Wallpaper #${post.id} removed from favorites")
            }
        }
    }

    fun applyWallpaper(post: Post, target: WallpaperTarget) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }
            val result = repository.applyPostAsWallpaper(post, target)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            userMessage = "Wallpaper #${post.id} applied to ${target.title}!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            errorMessage = "Failed to apply wallpaper: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}

