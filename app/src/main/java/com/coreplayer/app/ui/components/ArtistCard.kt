package com.coreplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coreplayer.app.ui.theme.CardSubtitleStyle
import com.coreplayer.app.ui.theme.CardTitleStyle
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.TextSecondary

/**
 * Artist card matching the "Недавние артисты" section in the mockup.
 *
 * Displays a 16dp rounded square artist photo loaded via Coil with placeholder fallback,
 * the artist's name (truncated with ellipsis), and subtitle "Артист".
 */
@Composable
fun ArtistCard(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String = "Артист",
    cardSize: Dp = 104.dp,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .width(cardSize)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = name,
                    tint = TextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.Person),
                    error = rememberVectorPainter(Icons.Filled.Person),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = CardTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = CardSubtitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun ArtistCardPreview() {
    com.coreplayer.app.ui.theme.CorePlayerTheme {
        ArtistCard(
            name = "The Neighbourhood",
            imageUrl = null,
            subtitle = "Артист"
        )
    }
}

