package ru.stasoft.mynotes.crypto

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Хранилище ключа базы.
 *
 * Случайный мастер-ключ (32 байта) шифрует саму базу SQLCipher. Сам он на диске лежит
 * только в зашифрованном виде: AES-GCM с ключом, выведенным из пароля через PBKDF2.
 * Неверный пароль определяется по тегу GCM, отдельного хэша пароля нет.
 * Забытый пароль восстановить нельзя.
 */
object Vault {
    private const val PREFS = "vault"
    private const val KDF_ITERATIONS = 210_000
    private const val MIN_PASSWORD = 6

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSetup(ctx: Context): Boolean = prefs(ctx).contains("wrapped")

    fun minPasswordLength(): Int = MIN_PASSWORD

    /** Создаёт новый мастер-ключ под пароль. Возвращает ключ; вызывающий обязан затереть его после открытия базы. */
    fun setup(ctx: Context, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD) { "Пароль слишком короткий" }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val master = ByteArray(32).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        val wrapped = cipher.doFinal(master)
        prefs(ctx).edit()
            .putString("salt", b64(salt))
            .putString("iv", b64(iv))
            .putString("wrapped", b64(wrapped))
            .putInt("fails", 0)
            .commit()
        return master
    }

    /** Возвращает мастер-ключ или null, если пароль неверный. */
    fun unlock(ctx: Context, password: CharArray): ByteArray? {
        val p = prefs(ctx)
        val salt = p.getString("salt", null)?.let(::unb64) ?: return null
        val iv = p.getString("iv", null)?.let(::unb64) ?: return null
        val wrapped = p.getString("wrapped", null)?.let(::unb64) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
            val master = cipher.doFinal(wrapped)
            p.edit().putInt("fails", 0).apply()
            master
        } catch (e: Exception) {
            p.edit()
                .putInt("fails", p.getInt("fails", 0) + 1)
                .putLong("lastFail", System.currentTimeMillis())
                .apply()
            null
        }
    }

    /** Сколько миллисекунд ещё нельзя пробовать пароль (замедление после неудачных попыток). */
    fun lockoutRemainingMs(ctx: Context): Long {
        val p = prefs(ctx)
        val fails = p.getInt("fails", 0)
        if (fails < 3) return 0
        val delaySec = minOf(1L shl minOf(fails - 3, 8), 300L)
        val until = p.getLong("lastFail", 0) + delaySec * 1000
        return maxOf(0L, until - System.currentTimeMillis())
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
