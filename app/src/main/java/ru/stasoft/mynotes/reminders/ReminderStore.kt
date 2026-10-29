package ru.stasoft.mynotes.reminders

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

data class IndexEntry(
    val id: Long,
    val title: String,
    val dueAt: Long,
    val leadDays: Int,
    val period: Int,
    val lastFired: Long = 0,
    val snoozeUntil: Long = 0,
)

/** Отметка «выполнено», сделанная из уведомления, пока база была закрыта. */
data class PendingDone(val id: Long, val doneAt: Long)

data class IndexData(val entries: List<IndexEntry> = emptyList(), val pending: List<PendingDone> = emptyList())

/**
 * Небольшой индекс напоминаний (название и время), чтобы уведомления приходили, пока база закрыта паролем.
 * Лежит в зашифрованном файле; ключ в Android Keystore. Текст заметок сюда не попадает.
 */
object ReminderStore {
    private const val FILE = "reminders.bin"
    private const val ALIAS = "mynotes_reminder_index_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(spec)
        return gen.generateKey()
    }

    @Synchronized
    fun load(ctx: Context): IndexData {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return IndexData()
        return try {
            val raw = f.readBytes()
            val iv = raw.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8))
            val entries = json.getJSONArray("entries").let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    IndexEntry(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        dueAt = o.getLong("dueAt"),
                        leadDays = o.getInt("leadDays"),
                        period = o.getInt("period"),
                        lastFired = o.optLong("lastFired", 0),
                        snoozeUntil = o.optLong("snoozeUntil", 0),
                    )
                }
            }
            val pending = json.getJSONArray("pending").let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    PendingDone(o.getLong("id"), o.getLong("doneAt"))
                }
            }
            IndexData(entries, pending)
        } catch (e: Exception) {
            IndexData()
        }
    }

    @Synchronized
    fun save(ctx: Context, data: IndexData) {
        val json = JSONObject()
        json.put("entries", JSONArray().also { a ->
            data.entries.forEach { e ->
                a.put(
                    JSONObject()
                        .put("id", e.id).put("title", e.title).put("dueAt", e.dueAt)
                        .put("leadDays", e.leadDays).put("period", e.period)
                        .put("lastFired", e.lastFired).put("snoozeUntil", e.snoozeUntil),
                )
            }
        })
        json.put("pending", JSONArray().also { a ->
            data.pending.forEach { p -> a.put(JSONObject().put("id", p.id).put("doneAt", p.doneAt)) }
        })
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeBytes(iv + enc)
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    @Synchronized
    fun update(ctx: Context, f: (IndexData) -> IndexData): IndexData {
        val n = f(load(ctx))
        save(ctx, n)
        return n
    }
}
