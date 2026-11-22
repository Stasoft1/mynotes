package ru.stasoft.mynotes.widgets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Кнопка виджета «Быстрый ввод». */
class QuickBtn(val label: String, val folderId: Long?, val template: String, val daily: Boolean = false)

/** Настройки одного виджета. kind: folder, note, dates, quick. theme: 0 авто, 1 светлая, 2 тёмная, 3 прозрачная. size: 0..2. */
class WidgetCfg(
    val kind: String = "folder",
    val title: String = "",
    val folderId: Long? = null,
    val noteId: Long? = null,
    val count: Int = 4,
    val theme: Int = 0,
    val size: Int = 1,
    val add: Boolean = true,
    val newestFirst: Boolean = true,
    val template: String = "",
    val quick: List<QuickBtn> = emptyList(),
    /** Папка дня: [folderId] — родительская, а работать нужно с папкой сегодняшней даты внутри неё. */
    val daily: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("kind", kind); put("title", title); put("count", count); put("theme", theme); put("size", size)
        put("add", add); put("newest", newestFirst); put("template", template); put("daily", daily)
        folderId?.let { put("folder", it) }
        noteId?.let { put("note", it) }
        put("quick", JSONArray().apply {
            quick.forEach { q -> put(JSONObject().put("l", q.label).put("t", q.template).put("d", q.daily).apply { q.folderId?.let { put("f", it) } }) }
        })
    }.toString()

    companion object {
        fun parse(s: String?): WidgetCfg? = try {
            val o = JSONObject(s ?: return null)
            val qa = o.optJSONArray("quick") ?: JSONArray()
            WidgetCfg(
                kind = o.optString("kind", "folder"), title = o.optString("title"),
                folderId = if (o.has("folder")) o.getLong("folder") else null,
                noteId = if (o.has("note")) o.getLong("note") else null,
                count = o.optInt("count", 4).coerceIn(2, 6), theme = o.optInt("theme", 0), size = o.optInt("size", 1).coerceIn(0, 2),
                add = o.optBoolean("add", true), newestFirst = o.optBoolean("newest", true), template = o.optString("template"), daily = o.optBoolean("daily", false),
                quick = (0 until qa.length()).map { i -> qa.getJSONObject(i).let { q -> QuickBtn(q.optString("l"), if (q.has("f")) q.getLong("f") else null, q.optString("t"), q.optBoolean("d", false)) } },
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Строка виджета; [day] — день (epochDay) для сроков, [sub] считается при показе. */
class WRow(val title: String, val sub: String, val id: Long, val day: Long? = null)

class WSnap(val title: String, val text: String, val rows: List<WRow>, val day: Long? = null)

object WidgetPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun get(ctx: Context, id: Int): WidgetCfg? = WidgetCfg.parse(p(ctx).getString("cfg_$id", null))
    fun put(ctx: Context, id: Int, c: WidgetCfg) = p(ctx).edit().putString("cfg_$id", c.toJson()).apply()
    fun remove(ctx: Context, id: Int) = p(ctx).edit().remove("cfg_$id").apply()

    /** Показывать ли на рабочем столе тексты заметок (выключено — виджеты остаются кнопками). */
    fun contentOn(ctx: Context) = p(ctx).getBoolean("content", true)
    fun setContent(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("content", on).apply()

    /** Секрет, которым подписаны нажатия виджетов: чужое приложение не сможет создавать записи через MainActivity. */
    fun token(ctx: Context): String {
        p(ctx).getString("token", null)?.let { return it }
        val t = java.util.UUID.randomUUID().toString()
        p(ctx).edit().putString("token", t).apply()
        return t
    }
}

/** Снимки данных виджетов: хранятся в файле, зашифрованном ключом Android Keystore. */
object WidgetStore {
    private const val ALIAS = "mynotes_widgets"
    private fun file(ctx: Context) = File(ctx.filesDir, "widgets.bin")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build(),
        )
        return g.generateKey()
    }

    fun save(ctx: Context, snaps: Map<Int, WSnap>) {
        try {
            val root = JSONObject()
            snaps.forEach { (id, s) ->
                root.put(id.toString(), JSONObject().put("t", s.title).put("x", s.text).apply { s.day?.let { put("y", it) } }.put("r", JSONArray().apply {
                    s.rows.forEach { r -> put(JSONObject().put("a", r.title).put("b", r.sub).put("i", r.id).apply { r.day?.let { d -> put("d", d) } }) }
                }))
            }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(root.toString().toByteArray())
            file(ctx).writeBytes(out)
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context): Map<Int, WSnap> = try {
        val f = file(ctx)
        if (!f.exists()) emptyMap() else {
            val b = f.readBytes()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12))
            val root = JSONObject(String(c.doFinal(b, 12, b.size - 12)))
            root.keys().asSequence().associate { k ->
                val o = root.getJSONObject(k)
                val ra = o.getJSONArray("r")
                k.toInt() to WSnap(
                    o.optString("t"), o.optString("x"),
                    (0 until ra.length()).map { i -> ra.getJSONObject(i).let { r -> WRow(r.optString("a"), r.optString("b"), r.optLong("i"), if (r.has("d")) r.getLong("d") else null) } },
                    if (o.has("y")) o.getLong("y") else null,
                )
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
