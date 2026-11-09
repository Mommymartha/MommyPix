package com.project.mommypix.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.preferences.BothScreenMode
import com.project.mommypix.data.preferences.UserPreferences
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.domain.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isApplyingWallpaper: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val currentPost: Post? = null,
    val homePost: Post? = null,
    val lockPost: Post? = null,
    val userPreferences: UserPreferences = UserPreferences(),
    val hasCredentials: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MommyPixApp
    private val repository = app.repository
    private val prefsRepository = app.preferencesRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepository.userPreferencesFlow.collectLatest { prefs ->
                _uiState.update {
                    it.copy(
                        userPreferences = prefs,
                        hasCredentials = repository.hasCredentials()
                    )
                }
            }
        }

        checkCredentialsAndLoad()
    }

    fun checkCredentialsAndLoad() {
        val hasCreds = repository.hasCredentials()
        _uiState.update { it.copy(hasCredentials = hasCreds) }
        if (hasCreds && _uiState.value.currentPost == null) {
            loadInitialImages()
        }
    }

    fun loadInitialImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val prefs = _uiState.value.userPreferences

            if (prefs.wallpaperTarget == WallpaperTarget.BOTH && prefs.bothScreenMode == BothScreenMode.DIFFERENT_IMAGES) {
                // Load two independent images
                val res1 = repository.fetchRandomPost(forceRefresh = true)
                val res2 = repository.fetchRandomPost(forceRefresh = true)

                if (res1.isSuccess && res2.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            homePost = res1.getOrNull(),
                            lockPost = res2.getOrNull(),
                            currentPost = res1.getOrNull()
                        )
                    }
                } else {
                    val err = res1.exceptionOrNull()?.localizedMessage
                        ?: res2.exceptionOrNull()?.localizedMessage
                        ?: "Failed to load posts"
                    _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                }
            } else {
                val result = repository.fetchRandomPost(forceRefresh = true)
                result.fold(
                    onSuccess = { post ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentPost = post,
                                homePost = post,
                                lockPost = post
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = err.localizedMessage ?: "Failed to fetch post")
                        }
                    }
                )
            }
        }
    }

    fun refreshRandomImage() {
        loadInitialImages()
    }

    fun nextImage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.fetchRandomPost(forceRefresh = false)
            result.fold(
                onSuccess = { post ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentPost = post,
                            homePost = post
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.localizedMessage ?: "Failed to get next post")
                    }
                }
            )
        }
    }

    fun refreshHomeImageOnly() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.fetchRandomPost(forceRefresh = true)
            result.fold(
                onSuccess = { post ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            homePost = post,
                            currentPost = post,
                            userMessage = "Home screen preview shuffled (#${post.id})"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun refreshLockImageOnly() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.fetchRandomPost(forceRefresh = true)
            result.fold(
                onSuccess = { post ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lockPost = post,
                            userMessage = "Lock screen preview shuffled (#${post.id})"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun applyHomeOnly() {
        val post = _uiState.value.homePost ?: _uiState.value.currentPost ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true, errorMessage = null) }
            val result = repository.applyPostAsWallpaper(post, WallpaperTarget.HOME)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, userMessage = "Applied #${post.id} to Home screen!")
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, errorMessage = "Failed to apply Home wallpaper: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun applyLockOnly() {
        val post = _uiState.value.lockPost ?: _uiState.value.currentPost ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true, errorMessage = null) }
            val result = repository.applyPostAsWallpaper(post, WallpaperTarget.LOCK)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, userMessage = "Applied #${post.id} to Lock screen!")
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, errorMessage = "Failed to apply Lock wallpaper: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun applyBothDual() {
        val state = _uiState.value
        val home = state.homePost ?: state.currentPost
        val lock = state.lockPost ?: state.currentPost
        if (home == null || lock == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true, errorMessage = null) }
            val result = if (state.userPreferences.bothScreenMode == BothScreenMode.DIFFERENT_IMAGES) {
                repository.applyDualDifferentWallpapers(home, lock)
            } else {
                repository.applyPostAsWallpaper(home, WallpaperTarget.BOTH)
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, userMessage = "Applied wallpapers to both Home and Lock screens!")
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, errorMessage = "Failed to apply wallpapers: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun downloadImage(post: Post? = _uiState.value.currentPost) {
        if (post == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0, errorMessage = null) }
            val result = repository.downloadAndSaveImage(post) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            result.fold(
                onSuccess = { file ->
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

    fun applyWallpaper(targetOverride: WallpaperTarget? = null) {
        val state = _uiState.value
        val target = targetOverride ?: state.userPreferences.wallpaperTarget

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true, errorMessage = null) }

            val result = if (target == WallpaperTarget.BOTH &&
                state.userPreferences.bothScreenMode == BothScreenMode.DIFFERENT_IMAGES &&
                state.homePost != null && state.lockPost != null
            ) {
                repository.applyDualDifferentWallpapers(state.homePost, state.lockPost)
            } else {
                val post = state.currentPost
                if (post != null) {
                    repository.applyPostAsWallpaper(post, target)
                } else {
                    Result.failure(Exception("No image available to set as wallpaper"))
                }
            }

            result.fold(
                onSuccess = {
                    val msg = when (target) {
                        WallpaperTarget.HOME -> "Home screen wallpaper applied successfully!"
                        WallpaperTarget.LOCK -> "Lock screen wallpaper applied successfully!"
                        WallpaperTarget.BOTH -> "Both wallpapers applied successfully!"
                    }
                    _uiState.update {
                        it.copy(isApplyingWallpaper = false, userMessage = msg)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isApplyingWallpaper = false,
                            errorMessage = "Failed to set wallpaper: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun toggleFavorite(post: Post) {
        viewModelScope.launch {
            val newFav = repository.toggleFavorite(post)
            _uiState.update { state ->
                val updatedCurrent = if (state.currentPost?.id == post.id) {
                    state.currentPost.copy(isFavorite = newFav)
                } else state.currentPost

                val updatedHome = if (state.homePost?.id == post.id) {
                    state.homePost.copy(isFavorite = newFav)
                } else state.homePost

                val updatedLock = if (state.lockPost?.id == post.id) {
                    state.lockPost.copy(isFavorite = newFav)
                } else state.lockPost

                state.copy(
                    currentPost = updatedCurrent,
                    homePost = updatedHome,
                    lockPost = updatedLock,
                    userMessage = if (newFav) "Added to Favorites" else "Removed from Favorites"
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
