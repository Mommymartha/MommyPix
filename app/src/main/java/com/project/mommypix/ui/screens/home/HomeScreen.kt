package com.project.mommypix.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project.mommypix.data.preferences.BothScreenMode
import com.project.mommypix.data.preferences.RatingFilter
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.domain.model.Post
import com.project.mommypix.ui.components.AppTopBar
import com.project.mommypix.ui.components.TagsFlowRow
import com.project.mommypix.ui.components.WallpaperCard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToPreview: (Long) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.checkCredentialsAndLoad()
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "MommyPix",
                canNavigateBack = false,
                onOpenFavorites = onNavigateToFavorites,
                onOpenHistory = onNavigateToHistory,
                onOpenSettings = onNavigateToSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Missing Credentials Banner
            if (!state.hasCredentials) {
                MissingCredentialsCard(onOpenSettings = onNavigateToSettings)
            }

            // Quick Content Rating Toggle (Both / Safe / NSFW)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RatingFilter.entries.forEach { filter ->
                    val isSelected = state.userPreferences.ratingFilter == filter
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setRatingFilter(filter) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (filter) {
                                    RatingFilter.BOTH -> "🎲 Both"
                                    RatingFilter.SAFE_ONLY -> "🛡️ Safe"
                                    RatingFilter.NSFW_ONLY -> "🔞 NSFW"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Dual Previews or Single Preview
            val isDualDifferentMode = state.userPreferences.wallpaperTarget == WallpaperTarget.BOTH &&
                    state.userPreferences.bothScreenMode == BothScreenMode.DIFFERENT_IMAGES

            if (isDualDifferentMode) {
                // Two separate previews: Home and Lock with individual buttons
                DualPreviewsSection(
                    homePost = state.homePost,
                    lockPost = state.lockPost,
                    isEnabled = state.hasCredentials && !state.isLoading && !state.isApplyingWallpaper && !state.isDownloading,
                    onPreviewHome = { state.homePost?.let { onNavigateToPreview(it.id) } },
                    onPreviewLock = { state.lockPost?.let { onNavigateToPreview(it.id) } },
                    onToggleHomeFavorite = { state.homePost?.let { viewModel.toggleFavorite(it) } },
                    onToggleLockFavorite = { state.lockPost?.let { viewModel.toggleFavorite(it) } },
                    onRefreshHomeOnly = { viewModel.refreshHomeImageOnly() },
                    onRefreshLockOnly = { viewModel.refreshLockImageOnly() },
                    onApplyHomeOnly = { viewModel.applyHomeOnly() },
                    onApplyLockOnly = { viewModel.applyLockOnly() },
                    onApplyBoth = { viewModel.applyBothDual() },
                    onRefreshBoth = { viewModel.loadInitialImages() }
                )
            } else {
                // Single large preview
                state.currentPost?.let { post ->
                    SinglePreviewSection(
                        post = post,
                        onPreviewClick = { onNavigateToPreview(post.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(post) }
                    )
                } ?: run {
                    if (state.hasCredentials && !state.isLoading) {
                        EmptyStateCard(onFetch = { viewModel.loadInitialImages() })
                    }
                }
            }

            // Loading / Progress Indicators
            if (state.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Fetching random image...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.isDownloading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Downloading image (${state.downloadProgress}%)...",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { state.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.isApplyingWallpaper) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Setting wallpaper...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Tags Section for single preview
            if (!isDualDifferentMode && state.currentPost != null) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TagsFlowRow(tags = state.currentPost?.tags.orEmpty())
            }

            // Action Buttons Section for Single Mode
            if (!isDualDifferentMode) {
                IndividualActionButtonsSection(
                    isEnabled = state.hasCredentials && !state.isLoading && !state.isApplyingWallpaper && !state.isDownloading,
                    onRandomClick = { viewModel.refreshRandomImage() },
                    onNextClick = { viewModel.nextImage() },
                    onDownloadClick = { viewModel.downloadImage() },
                    onApplyHomeOnly = { viewModel.applyHomeOnly() },
                    onApplyLockOnly = { viewModel.applyLockOnly() },
                    onApplyBoth = { viewModel.applyWallpaper(WallpaperTarget.BOTH) }
                )
            }
        }
    }
}

@Composable
private fun MissingCredentialsCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenSettings() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = "API Key Required",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rule34 API Credentials Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Tap here to configure your User ID and API Key in Settings to fetch wallpapers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun SinglePreviewSection(
    post: Post,
    onPreviewClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    WallpaperCard(
        post = post,
        headerTitle = null,
        onCardClick = onPreviewClick,
        onToggleFavorite = onToggleFavorite
    )
}

@Composable
private fun DualPreviewsSection(
    homePost: Post?,
    lockPost: Post?,
    isEnabled: Boolean,
    onPreviewHome: () -> Unit,
    onPreviewLock: () -> Unit,
    onToggleHomeFavorite: () -> Unit,
    onToggleLockFavorite: () -> Unit,
    onRefreshHomeOnly: () -> Unit,
    onRefreshLockOnly: () -> Unit,
    onApplyHomeOnly: () -> Unit,
    onApplyLockOnly: () -> Unit,
    onApplyBoth: () -> Unit,
    onRefreshBoth: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dual Independent Wallpapers",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            FilledTonalButton(
                onClick = onRefreshBoth,
                enabled = isEnabled,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Shuffle Both", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Home Column with Card & Individual Buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                homePost?.let { post ->
                    WallpaperCard(
                        post = post,
                        headerTitle = "Home Screen",
                        onCardClick = onPreviewHome,
                        onToggleFavorite = onToggleHomeFavorite
                    )
                }

                // Individual Home Controls: One below the other to avoid congestion
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onRefreshHomeOnly,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle Home", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onApplyHomeOnly,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Home", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Lock Column with Card & Individual Buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lockPost?.let { post ->
                    WallpaperCard(
                        post = post,
                        headerTitle = "Lock Screen",
                        onCardClick = onPreviewLock,
                        onToggleFavorite = onToggleLockFavorite
                    )
                }

                // Individual Lock Controls: One below the other to avoid congestion
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onRefreshLockOnly,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle Lock", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onApplyLockOnly,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Apply Both at once button
        Button(
            onClick = onApplyBoth,
            enabled = isEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply Both Wallpapers (Home + Lock)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyStateCard(onFetch: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Casino,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ready to fetch random wallpapers",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onFetch,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Fetch Random Image")
            }
        }
    }
}

@Composable
private fun IndividualActionButtonsSection(
    isEnabled: Boolean,
    onRandomClick: () -> Unit,
    onNextClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onApplyHomeOnly: () -> Unit,
    onApplyLockOnly: () -> Unit,
    onApplyBoth: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Individual Apply Buttons: Home Only, Lock Only, Both
        Text(
            text = "Apply Wallpaper Individually",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onApplyHomeOnly,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Home", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onApplyLockOnly,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lock", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onApplyBoth,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Both", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Secondary Row: Random, Next, Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onRandomClick,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Random")
            }

            FilledTonalButton(
                onClick = onNextClick,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Next")
            }

            FilledTonalButton(
                onClick = onDownloadClick,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save")
            }
        }
    }
}
