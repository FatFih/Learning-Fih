package com.example.webapp

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthRepository {
    suspend fun register(dao: UserDao, username: String, password: String): Boolean {
        val normalizedUsername = normalizeUsername(username)
        if (normalizedUsername.isBlank() || password.isEmpty()) return false

        val digest = withContext(Dispatchers.Default) {
            PasswordUtil.createDigest(password)
        }
        return dao.insert(
            User(normalizedUsername, digest.salt, digest.hash)
        ) != -1L
    }

    suspend fun login(dao: UserDao, username: String, password: String): Boolean {
        val normalizedUsername = normalizeUsername(username)
        if (normalizedUsername.isBlank() || password.isEmpty()) return false

        val user = dao.findByUsername(normalizedUsername) ?: return false
        return withContext(Dispatchers.Default) {
            PasswordUtil.verify(password, user.passwordSalt, user.passwordHash)
        }
    }

    fun normalizeUsername(username: String): String = username.trim().lowercase(Locale.ROOT)
}