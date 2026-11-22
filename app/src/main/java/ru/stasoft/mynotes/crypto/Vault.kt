package ru.stasoft.mynotes.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
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

    fun isSetup(ctx: Context): Boolean = prefs(ctx).contains("wrapped") || prefs(ctx).contains("dev_wrapped")

    /** Режим без пароля: мастер-ключ лежит под защитой ключа Android Keystore, база открывается сразу. */
    fun isPasswordless(ctx: Context): Boolean = prefs(ctx).contains("dev_wrapped")

    private const val DEV_ALIAS = "mynotes_device_key"

    private fun devKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(DEV_ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(DEV_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return g.generateKey()
    }

    private fun deviceWrap(ctx: Context, master: ByteArray) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, devKey())
        val w = c.doFinal(master)
        prefs(ctx).edit().putString("dev_wrapped", b64(w)).putString("dev_iv", b64(c.iv)).commit()
    }

    /** Мастер-ключ в режиме без пароля или null, если ключ телефона недоступен. */
    fun deviceUnlock(ctx: Context): ByteArray? = try {
        val p = prefs(ctx)
        val w = unb64(p.getString("dev_wrapped", null) ?: return null)
        val iv = unb64(p.getString("dev_iv", null) ?: return null)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, devKey(), GCMParameterSpec(128, iv))
        c.doFinal(w)
    } catch (_: Exception) {
        null
    }

    /** Первый запуск без пароля. */
    fun setupPasswordless(ctx: Context): ByteArray {
        val master = ByteArray(32).also { SecureRandom().nextBytes(it) }
        deviceWrap(ctx, master)
        return master
    }

    /** Переводит базу в режим без пароля: [master] — ключ, полученный по паролю. */
    fun makePasswordless(ctx: Context, master: ByteArray) {
        deviceWrap(ctx, master)
        prefs(ctx).edit().remove("salt").remove("iv").remove("wrapped").remove("fails").remove("lastFail").commit()
        BiometricVault.disable(ctx)
    }

    /** Снова включает пароль на тот же ключ базы (перешифровывать базу не нужно). */
    fun makePassword(ctx: Context, password: CharArray): Boolean {
        val master = deviceUnlock(ctx) ?: return false
        wrapWithPassword(ctx, master, password)
        prefs(ctx).edit().remove("dev_wrapped").remove("dev_iv").commit()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(DEV_ALIAS)
        } catch (_: Exception) {
        }
        master.fill(0)
        return true
    }

    /** Ключ телефона из резервной копии: в копии режим без пароля хранится открытым ключом, здесь он закрепляется заново. */
    fun finishRestore(ctx: Context) {
        val p = prefs(ctx)
        val plain = p.getString("plain_master", null)
        if (plain != null) {
            try {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(DEV_ALIAS)
            } catch (_: Exception) {
            }
            val m = unb64(plain)
            deviceWrap(ctx, m)
            m.fill(0)
            p.edit().remove("plain_master").commit()
        }
    }

    /** Для резервной копии: открытый мастер-ключ в режиме без пароля, иначе null. */
    fun masterForBackup(ctx: Context): String? = if (isPasswordless(ctx)) deviceUnlock(ctx)?.let { b64(it) } else null

    fun minPasswordLength(): Int = MIN_PASSWORD

    /** Создаёт новый мастер-ключ под пароль. Возвращает ключ; вызывающий обязан затереть его после открытия базы. */
    fun setup(ctx: Context, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD) { "Пароль слишком короткий" }
        val master = ByteArray(32).also { SecureRandom().nextBytes(it) }
        wrapWithPassword(ctx, master, password)
        return master
    }

    private fun wrapWithPassword(ctx: Context, master: ByteArray, password: CharArray) {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        val wrapped = cipher.doFinal(master)
        prefs(ctx).edit()
            .putString("salt", b64(salt))
            .putString("iv", b64(iv))
            .putString("wrapped", b64(wrapped))
            .putInt("fails", 0)
            .commit()
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
