package ru.stasoft.mynotes.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Вход по отпечатку. Мастер-ключ базы дополнительно шифруется ключом из Android Keystore,
 * который можно использовать только сразу после успешного отпечатка (сильная биометрия).
 * Ключ уничтожается системой, если в телефон добавили новый отпечаток: тогда нужно ввести пароль
 * и включить вход по отпечатку заново.
 */
object BiometricVault {
    private const val ALIAS = "mynotes_biometric_key"
    private const val PREFS = "vault"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAvailable(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).contains("bio_wrapped")

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(spec)
        return gen.generateKey()
    }

    /** Шифр для записи ключа (нужно пройти отпечаток с этим шифром). */
    fun encryptCipher(): Cipher {
        disableKeyOnly() // всегда новый ключ при включении
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    }

    /** Шифр для чтения ключа или null, если отпечаток больше недействителен. */
    fun decryptCipher(ctx: Context): Cipher? {
        val iv = prefs(ctx).getString("bio_iv", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: run { disable(ctx); return null }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(ctx)
            null
        } catch (e: Exception) {
            null
        }
    }

    fun store(ctx: Context, cipher: Cipher, master: ByteArray) {
        val wrapped = cipher.doFinal(master)
        prefs(ctx).edit()
            .putString("bio_wrapped", Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString("bio_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    fun unwrap(ctx: Context, cipher: Cipher): ByteArray? {
        val wrapped = prefs(ctx).getString("bio_wrapped", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return cipher.doFinal(wrapped)
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().remove("bio_wrapped").remove("bio_iv").commit()
        disableKeyOnly()
    }

    private fun disableKeyOnly() {
        try {
            val ks = keyStore()
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }
}
