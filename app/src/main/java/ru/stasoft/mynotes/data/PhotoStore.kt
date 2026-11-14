package ru.stasoft.mynotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Фото заметок вне базы: уменьшенный JPEG (до 1600 px, ~200–400 КБ), зашифрованный ключом из мастер-ключа базы,
 * в личной папке приложения. Благодаря этому база не растёт, а файлы переносятся в резервных копиях и открываются
 * только тем же паролем. Ключ живёт только в памяти, пока приложение разблокировано.
 */
object PhotoStore {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 80
    @Volatile private var key: SecretKeySpec? = null
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount
    }

    fun setKey(master: ByteArray?) {
        cache.evictAll()
        key = master?.let { SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(it + "photos".toByteArray()), "AES") }
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "photos").also { it.mkdirs() }

    private fun crypt(mode: Int, data: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, key ?: throw IllegalStateException("приложение заблокировано"), GCMParameterSpec(128, iv))
        return c.doFinal(data)
    }

    /** Уменьшает снимок с учётом поворота, сжимает и сохраняет зашифрованным. Возвращает имя файла. */
    fun save(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("не удалось открыть снимок")
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("это не изображение")
        val rotation = try {
            when (android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                3 -> 180f; 6 -> 90f; 8 -> 270f; else -> 0f
            }
        } catch (_: Exception) { 0f }
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f || rotation != 0f) {
            val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        bmp.recycle()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val name = UUID.randomUUID().toString().replace("-", "") + ".p"
        File(dir(ctx), name).writeBytes(iv + crypt(Cipher.ENCRYPT_MODE, out.toByteArray(), iv))
        return name
    }

    /** Расшифрованный JPEG (для просмотра и отправки). */
    fun bytes(ctx: Context, name: String): ByteArray? = try {
        val raw = File(dir(ctx), name).readBytes()
        crypt(Cipher.DECRYPT_MODE, raw.copyOfRange(12, raw.size), raw.copyOfRange(0, 12))
    } catch (_: Exception) { null }

    /** Картинка не больше [maxSide] по длинной стороне (с кэшем). */
    fun bitmap(ctx: Context, name: String, maxSide: Int): Bitmap? {
        val ck = "$name@$maxSide"
        cache.get(ck)?.let { return it }
        val b = bytes(ctx, name) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(ck, bmp)
        return bmp
    }

    fun delete(ctx: Context, name: String) {
        File(dir(ctx), name).delete()
    }

    /** Удаляет файлы, на которые не осталось ссылок в базе. */
    fun sweep(ctx: Context, keep: Set<String>) {
        dir(ctx).listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }
}
