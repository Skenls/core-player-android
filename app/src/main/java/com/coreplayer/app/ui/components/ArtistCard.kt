package com.coreplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * Artist card matching the "Недавние артисты" section in helpers/ mockup.
 *
 * Displays a 22dp squircle artist photo loaded via Coil,
 * the artist's name (truncated with ellipsis), and subtitle "Артист".
 */
@Composable
fun ArtistCard(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String = "Артист",
    cardSize: Dp = 112.dp,
    onClick: () -> Unit = {}
) {
    val cornerShape = RoundedCornerShape(22.dp)
    val fallbackGradient = remember(name) {
        val palettes = listOf(
            listOf(Color(0xFF1E2838), Color(0xFF131A26)),
            listOf(Color(0xFF231F33), Color(0xFF171324)),
            listOf(Color(0xFF1B2F38), Color(0xFF111D24)),
            listOf(Color(0xFF2E241E), Color(0xFF1C1612))
        )
        val idx = abs(name.hashCode()) % palettes.size
        Brush.verticalGradient(palettes[idx])
    }

    val cleanedName = com.coreplayer.app.ui.util.cleanArtistName(name).ifBlank { name }

    Column(
        modifier = modifier
            .width(cardSize)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(cornerShape)
                .background(fallbackGradient),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = cleanedName,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(46.dp)
                )
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = cleanedName,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.Person),
                    error = rememberVectorPainter(Icons.Filled.Person),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = cleanedName,
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = Color(0xFF8E95A5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun ArtistCardPreview() {
    CorePlayerTheme {
        ArtistCard(
            name = "The Neighbourhood",
            imageUrl = null
        )
    }
}
