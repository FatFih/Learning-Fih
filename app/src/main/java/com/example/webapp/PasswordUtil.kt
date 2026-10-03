package com.example.webapp

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordDigest(val salt: String, val hash: String)

object PasswordUtil {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private val random = SecureRandom()

    fun createDigest(password: String): PasswordDigest {
        val salt = ByteArray(16).also(random::nextBytes)
        return PasswordDigest(encode(salt), encode(derive(password, salt)))
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean = try {
        MessageDigest.isEqual(
            decode(expectedHash),
            derive(password, decode(salt))
        )
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}