package com.coreplayer.app.data.repository

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchHistoryManagerTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val storage = mutableMapOf<String, String?>()
    private lateinit var historyManager: SearchHistoryManager

    @Before
    fun setUp() {
        storage.clear()
        mockEditor = mockk(relaxed = true)
        every { mockEditor.putString(any(), any()) } answers {
            storage[firstArg()] = secondArg()
            mockEditor
        }
        every { mockEditor.remove(any()) } answers {
            storage.remove(firstArg())
            mockEditor
        }
        every { mockEditor.apply() } returns Unit

        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getString(any(), any()) } answers {
            storage[firstArg()] ?: secondArg()
        }
        every { mockPrefs.edit() } returns mockEditor

        historyManager = SearchHistoryManager(mockPrefs)
    }

    @Test
    fun initialHistory_isEmpty() {
        assertTrue(historyManager.history.value.isEmpty())
    }

    @Test
    fun addQuery_addsToFrontAndPersists() {
        historyManager.addQuery("Radiohead")
        assertEquals(listOf("Radiohead"), historyManager.history.value)

        historyManager.addQuery("Kai Angel")
        assertEquals(listOf("Kai Angel", "Radiohead"), historyManager.history.value)

        // Adding duplicate moves it to the front
        historyManager.addQuery("radiohead")
        assertEquals(listOf("radiohead", "Kai Angel"), historyManager.history.value)
    }

    @Test
    fun removeQuery_removesCorrectItem() {
        historyManager.addQuery("A")
        historyManager.addQuery("B")
        historyManager.addQuery("C")

        historyManager.removeQuery("b")
        assertEquals(listOf("C", "A"), historyManager.history.value)
    }

    @Test
    fun clearHistory_clearsAllItems() {
        historyManager.addQuery("Item 1")
        historyManager.addQuery("Item 2")
        assertEquals(2, historyManager.history.value.size)

        historyManager.clearHistory()
        assertTrue(historyManager.history.value.isEmpty())
    }

    @Test
    fun maxHistory_cappedAt10() {
        for (i in 1..15) {
            historyManager.addQuery("Query $i")
        }
        assertEquals(10, historyManager.history.value.size)
        assertEquals("Query 15", historyManager.history.value.first())
    }
}
