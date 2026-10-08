package com.coreplayer.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchHistoryManager(
    private val prefs: SharedPreferences,
    moshi: Moshi = Moshi.Builder().build()
) {
    constructor(context: Context, moshi: Moshi = Moshi.Builder().build()) : this(
        context.getSharedPreferences("core_player_search_history", Context.MODE_PRIVATE),
        moshi
    )

    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(listType)

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun loadHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = _history.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val trimmedList = current.take(MAX_HISTORY)
        saveHistory(trimmedList)
    }

    fun removeQuery(query: String) {
        val current = _history.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        saveHistory(current)
    }

    fun clearHistory() {
        saveHistory(emptyList())
    }

    private fun saveHistory(list: List<String>) {
        _history.value = list
        val json = adapter.toJson(list)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    companion object {
        private const val KEY_HISTORY = "recent_queries"
        private const val MAX_HISTORY = 10
    }
}
