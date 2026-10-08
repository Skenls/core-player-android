package com.coreplayer.app.ui.screens.nowplaying

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coreplayer.app.data.model.LyricLine
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary

/**
 * Computes the 0-based active lyric line index for [currentPositionMs].
 *
 * Returns:
 * - `-1` if [lyrics] is empty or [currentPositionMs] is before the first line.
 * - The index of the latest line whose [LyricLine.timestampMs] <= [currentPositionMs].
 */
fun findActiveLyricIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
    if (lyrics.isEmpty() || currentPositionMs < 0L) return -1
    return lyrics.indexOfLast { it.timestampMs <= currentPositionMs }
}

/**
 * Synchronized karaoke lyrics view.
 *
 * Features:
 * - LazyColumn with auto-scroll to the currently active lyric line.
 * - Active line rendered in bold white with slight scale animation (1.05x).
 * - Inactive lines rendered with 40% opacity.
 * - Tap any line to seek playback to its [LyricLine.timestampMs].
 * - Loading indicator and empty fallback message.
 */
@Composable
fun SyncedLyricsView(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Загрузка текста песни...",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage ?: "Текст песни недоступен",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val activeIndex by remember(lyrics, currentPositionMs) {
        derivedStateOf { findActiveLyricIndex(lyrics, currentPositionMs) }
    }

    val listState = rememberLazyListState()

    val isScrolling = listState.isScrollInProgress

    // Smooth auto-scroll to active line, centering it one line higher in view
    LaunchedEffect(activeIndex, isScrolling) {
        if (!isScrolling && activeIndex in lyrics.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 4) else 0
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = centerOffset
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 70.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = lyrics,
            key = { index, line -> "${index}_${line.timestampMs}" }
        ) { index, line ->
            val isActive = index == activeIndex

            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else if (isScrolling) 0.65f else 0.35f,
                animationSpec = tween(150),
                label = "lyricAlpha_$index"
            )

            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.05f else 1f,
                animationSpec = tween(150),
                label = "lyricScale_$index"
            )

            // Remove blur when user is scrolling so text is instantly readable,
            // and use static blur without continuous animateDpAsState to prevent lag on battery saver
            val blurModifier = if (!isActive && !isScrolling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(2.dp)
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(blurModifier)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = { onSeekTo(line.timestampMs) }
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = line.text.ifBlank { "♪" },
                    color = TextPrimary.copy(alpha = alpha),
                    fontSize = if (isActive) 23.sp else 19.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = if (isActive) 32.sp else 26.sp,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun SyncedLyricsViewPreview() {
    val sampleLyrics = listOf(
        LyricLine(1000L, "First line of the song"),
        LyricLine(4000L, "Singing with the feeling"),
        LyricLine(8000L, "Active line highlighted in bold white"),
        LyricLine(12000L, "Upcoming lyrics fade out"),
        LyricLine(16000L, "Final synchronized verse")
    )
    CorePlayerTheme {
        SyncedLyricsView(
            lyrics = sampleLyrics,
            currentPositionMs = 8500L,
            onSeekTo = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun SyncedLyricsViewEmptyPreview() {
    CorePlayerTheme {
        SyncedLyricsView(
            lyrics = emptyList(),
            currentPositionMs = 0L,
            onSeekTo = {}
        )
    }
}
