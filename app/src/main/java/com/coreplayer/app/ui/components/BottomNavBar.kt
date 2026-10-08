package com.coreplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coreplayer.app.ui.theme.CorePlayerTheme

/**
 * Bottom navigation destinations matching helpers/ mockup.
 */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    data object Home : BottomNavItem(
        route = "home",
        label = "Главная",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home
    )

    data object Search : BottomNavItem(
        route = "search",
        label = "Поиск",
        icon = Icons.Outlined.Search,
        selectedIcon = Icons.Filled.Search
    )

    data object Browse : BottomNavItem(
        route = "browse",
        label = "Все треки",
        icon = Icons.AutoMirrored.Outlined.QueueMusic,
        selectedIcon = Icons.AutoMirrored.Filled.QueueMusic
    )

    data object Library : BottomNavItem(
        route = "library",
        label = "Медиатека",
        icon = Icons.Outlined.LibraryMusic,
        selectedIcon = Icons.Filled.LibraryMusic
    )

    companion object {
        val items = listOf(Home, Search, Browse, Library)
    }
}

/**
 * Bottom navigation bar strictly matching the mockup.
 *
 * Has 3 items (Home, Search, Library), dark obsidian background (#080C14),
 * seamless gradient transition, and crisp active/inactive colors.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF080C14))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem.items.forEach { item ->
                val selected = currentRoute == item.route
                val contentColor = if (selected) Color.White else Color(0xFF8E95A5)
                val icon = if (selected) item.selectedIcon else item.icon

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 28.dp),
                            onClick = { onNavigate(item.route) }
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun BottomNavBarPreview() {
    CorePlayerTheme {
        BottomNavBar(
            currentRoute = BottomNavItem.Home.route,
            onNavigate = {}
        )
    }
}
