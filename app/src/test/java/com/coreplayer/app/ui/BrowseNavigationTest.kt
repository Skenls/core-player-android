package com.coreplayer.app.ui

import com.coreplayer.app.ui.components.BottomNavItem
import com.coreplayer.app.ui.navigation.Screen
import com.coreplayer.app.ui.screens.browse.BrowseTab
import com.coreplayer.app.ui.screens.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseNavigationTest {

    @Test
    fun browseTabs_containExpectedEntries() {
        assertEquals(3, BrowseTab.entries.size)
        assertEquals("Альбомы", BrowseTab.ALBUMS.title)
        assertEquals("Артисты", BrowseTab.ARTISTS.title)
        assertEquals("Треки", BrowseTab.TRACKS.title)
    }

    @Test
    fun libraryTabs_containExpectedEntries() {
        assertEquals(2, LibraryTab.entries.size)
        assertEquals("Мои треки", LibraryTab.FAVORITES.title)
        assertEquals("Плейлисты", LibraryTab.PLAYLISTS.title)
    }

    @Test
    fun bottomNavItems_matchRoutesAndOrder() {
        assertEquals(4, BottomNavItem.items.size)
        assertEquals(Screen.Home.route, BottomNavItem.items[0].route)
        assertEquals(Screen.Search.route, BottomNavItem.items[1].route)
        assertEquals(Screen.Browse.route, BottomNavItem.items[2].route)
        assertEquals(Screen.Library.route, BottomNavItem.items[3].route)
    }
}
