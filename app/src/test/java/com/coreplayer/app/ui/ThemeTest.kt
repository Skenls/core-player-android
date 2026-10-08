package com.coreplayer.app.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coreplayer.app.ui.components.BottomNavItem
import com.coreplayer.app.ui.components.formatTracksCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun colorTokens_matchMockupPalette() {
        assertEquals(Color(0xFF0B0F17), DarkBackground)
        assertEquals(Color(0xFF131A26), DarkSurface)
        assertEquals(Color(0xFF1A2232), DarkSurfaceVariant)
        assertEquals(Color(0xFFFF5722), AccentOrange)
        assertEquals(Color(0xFFFF6422), AccentOrangeVibrant)
        assertEquals(Color(0xFFFFFFFF), TextPrimary)
        assertEquals(Color(0xFF8E95A5), TextSecondary)
        assertEquals(Color(0xFF0D121D), BottomBarBackground)
        assertEquals(Color(0xFF1E2638), SubtleDivider)
    }

    @Test
    fun colorScheme_configuredProperly() {
        assertEquals(DarkBackground, CorePlayerColorScheme.background)
        assertEquals(DarkSurface, CorePlayerColorScheme.surface)
        assertEquals(DarkSurfaceVariant, CorePlayerColorScheme.surfaceVariant)
        assertEquals(TextPrimary, CorePlayerColorScheme.primary)
        assertEquals(TextPrimary, CorePlayerColorScheme.onBackground)
        assertEquals(TextPrimary, CorePlayerColorScheme.onSurface)
        assertEquals(TextSecondary, CorePlayerColorScheme.onSurfaceVariant)
        assertEquals(SubtleDivider, CorePlayerColorScheme.outline)
    }

    @Test
    fun typography_hasExpectedWeightsAndSizes() {
        assertEquals(FontWeight.Bold, CoreLogoStyle.fontWeight)
        assertEquals(38.sp, CoreLogoStyle.fontSize)

        assertEquals(FontWeight.Bold, SectionTitleStyle.fontWeight)
        assertEquals(20.sp, SectionTitleStyle.fontSize)

        assertEquals(FontWeight.Bold, BannerTitleStyle.fontWeight)
        assertEquals(22.sp, BannerTitleStyle.fontSize)
        assertEquals(TextSecondary, BannerSubtitleStyle.color)

        assertEquals(FontWeight.SemiBold, CardTitleStyle.fontWeight)
        assertEquals(14.sp, CardTitleStyle.fontSize)
        assertEquals(TextSecondary, CardSubtitleStyle.color)

        assertEquals(CoreLogoStyle, CorePlayerTypography.headlineMedium)
        assertEquals(SectionTitleStyle, CorePlayerTypography.titleLarge)
        assertEquals(BannerTitleStyle, CorePlayerTypography.titleMedium)
        assertEquals(CardTitleStyle, CorePlayerTypography.titleSmall)
    }

    @Test
    fun shapes_configuredProperly() {
        // Shapes verify corner rounding: 8dp, 16dp, 20dp, 28dp
        assertEquals(CornerSize(8.dp), CorePlayerShapes.small.topStart)
        assertEquals(CornerSize(16.dp), CorePlayerShapes.medium.topStart)
        assertEquals(CornerSize(20.dp), CorePlayerShapes.large.topStart)
        assertEquals(CornerSize(28.dp), CorePlayerShapes.extraLarge.topStart)
    }

    @Test
    fun bottomNavItems_matchRequirements() {
        assertEquals(4, BottomNavItem.items.size)

        val home = BottomNavItem.items[0]
        assertEquals("home", home.route)
        assertEquals("Главная", home.label)

        val search = BottomNavItem.items[1]
        assertEquals("search", search.route)
        assertEquals("Поиск", search.label)

        val browse = BottomNavItem.items[2]
        assertEquals("browse", browse.route)
        assertEquals("Все треки", browse.label)

        val library = BottomNavItem.items[3]
        assertEquals("library", library.route)
        assertEquals("Медиатека", library.label)
    }

    @Test
    fun formatTracksCount_handlesPluralsMatchingMockup() {
        assertEquals("Плейлист · 1 трек", formatTracksCount(1))
        assertEquals("Плейлист · 2 трека", formatTracksCount(2))
        assertEquals("Плейлист · 4 трека", formatTracksCount(4))
        assertEquals("Плейлист · 5 треков", formatTracksCount(5))
        assertEquals("Плейлист · 11 треков", formatTracksCount(11))
        assertEquals("Плейлист · 14 треков", formatTracksCount(14))
        assertEquals("Плейлист · 21 трек", formatTracksCount(21))
        assertEquals("Плейлист · 57 треков", formatTracksCount(57))
        assertEquals("Плейлист · 82 трека", formatTracksCount(82))
        assertEquals("Плейлист · 124 трека", formatTracksCount(124))
    }
}
