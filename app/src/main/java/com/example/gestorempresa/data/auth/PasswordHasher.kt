package com.example.gestorempresa.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordDigest(val hash: String, val salt: String, val iterations: Int)

/** Autenticación LOCAL para esta práctica; no sustituye un servidor de identidad. */
class PasswordHasher {
    fun crear(password: String): PasswordDigest {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PasswordDigest(encode(derivar(password, salt, ITERATIONS)), encode(salt), ITERATIONS)
    }

    fun comprobar(password: String, digest: PasswordDigest): Boolean {
        if (digest.iterations !in 100_000..1_000_000) return false
        return try {
            val expected = Base64.getDecoder().decode(digest.hash)
            val salt = Base64.getDecoder().decode(digest.salt)
            if (salt.size != 16 || expected.size != 32) false
            else MessageDigest.isEqual(expected, derivar(password, salt, digest.iterations))
        } catch (_: IllegalArgumentException) { false }
    }

    private fun derivar(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { chars.fill('\u0000'); spec.clearPassword() }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    companion object { const val ITERATIONS = 600_000 }
}
