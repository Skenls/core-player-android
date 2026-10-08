package com.coreplayer.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import androidx.compose.ui.graphics.Color
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.player.formatTime
import com.coreplayer.app.ui.screens.nowplaying.resolveCoverUrl
import com.coreplayer.app.ui.theme.CardTitleStyle
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurface
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.SectionTitleStyle
import com.coreplayer.app.ui.theme.SubtleDivider
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    libraryRepository: LibraryRepository,
    playerController: PlayerControllerManager,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(300) // Debounce
        try {
            results = libraryRepository.search(trimmed)
        } catch (e: Exception) {
            android.util.Log.e("SearchScreen", "Search failed for query '$trimmed'", e)
            results = emptyList()
        } finally {
            isLoading = false
        }
    }

    SearchScreenContent(
        query = query,
        onQueryChange = { query = it },
        onClearQuery = { query = "" },
        results = results,
        isLoading = isLoading,
        baseUrl = playerController.baseUrl,
        onTrackClick = { track ->
            playerController.playTrack(track, results)
        },
        modifier = modifier
    )
}

@Composable
fun SearchScreenContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    results: List<TrackDto>,
    isLoading: Boolean,
    baseUrl: String,
    onTrackClick: (TrackDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Поиск",
            style = SectionTitleStyle,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск треков, артистов...", color = TextSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Поиск",
                    tint = if (query.isNotBlank()) Color.White else TextSecondary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Очистить",
                            tint = TextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = SubtleDivider,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (query.isBlank()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Введите поисковый запрос",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else if (results.isEmpty()) {
                Text(
                    text = "Ничего не найдено",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.id }) { track ->
                        SearchTrackItem(
                            track = track,
                            baseUrl = baseUrl,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTrackItem(
    track: TrackDto,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = resolveCoverUrl(track.coverArt, baseUrl)
            if (coverUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = track.title,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.MusicNote),
                    error = rememberVectorPainter(Icons.Filled.MusicNote),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = CardTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = track.artist.ifBlank { "Неизвестный исполнитель" },
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        if (track.duration > 0) {
            Text(
                text = formatTime(track.duration * 1000L),
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun SearchScreenPreview() {
    CorePlayerTheme {
        SearchScreenContent(
            query = "Radiohead",
            onQueryChange = {},
            onClearQuery = {},
            results = listOf(
                TrackDto(id = "1", title = "Karma Police", artist = "Radiohead", duration = 264),
                TrackDto(id = "2", title = "Paranoid Android", artist = "Radiohead", duration = 383)
            ),
            isLoading = false,
            baseUrl = "",
            onTrackClick = {}
        )
    }
}
