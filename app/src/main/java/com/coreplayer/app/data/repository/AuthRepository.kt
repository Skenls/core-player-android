package com.coreplayer.app.data.repository

import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.api.InMemoryCookieJar
import com.coreplayer.app.data.model.LoginRequest
import com.coreplayer.app.data.model.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val api: CorePlayerApi,
    private val cookieJar: InMemoryCookieJar? = null
) {
    private val _currentUser = MutableStateFlow<UserDto?>(null)
    val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    suspend fun login(username: String, password: String): UserDto {
        val user = api.login(LoginRequest(username = username, password = password))
        _currentUser.value = user
        return user
    }

    suspend fun getCurrentUser(): UserDto {
        val user = api.getCurrentUser()
        _currentUser.value = user
        return user
    }

    suspend fun logout() {
        try {
            api.logout()
        } finally {
            _currentUser.value = null
            cookieJar?.clear()
        }
    }

    fun clearSession() {
        _currentUser.value = null
        cookieJar?.clear()
    }
}
