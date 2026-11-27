package ru.stasoft.mynotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Данные из QR-кода кассового чека: t=20261126T1234&s=470.00&fn=…&i=…&fp=…&n=1. */
class ReceiptQr(val dateTime: LocalDateTime?, val sum: Double, val raw: String) {
    val dateText: String get() = dateTime?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")).orEmpty()
    val sumText: String get() = if (sum == Math.rint(sum)) sum.toLong().toString() else String.format(java.util.Locale.US, "%.2f", sum).trimEnd('0').trimEnd('.')
}

fun parseReceiptQr(text: String): ReceiptQr? {
    val map = text.trim().split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
    }.toMap()
    val s = map["s"]?.replace(',', '.')?.toDoubleOrNull() ?: return null
    if (map["t"] == null || (map["fn"] == null && map["fp"] == null)) return null
    val t = map["t"]!!
    val dt = listOf("yyyyMMdd'T'HHmmss", "yyyyMMdd'T'HHmm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm").firstNotNullOfOrNull {
        try { LocalDateTime.parse(t, DateTimeFormatter.ofPattern(it)) } catch (_: Exception) { null }
    }
    return ReceiptQr(dt, s, text)
}

/** Работа со снимком для распознавания: чтение с поворотом, поиск QR-кода, сжатие для отправки. */
object PhotoRecog {
    /** Читает снимок, уменьшая длинную сторону до [maxSide], и поворачивает по EXIF. */
    fun load(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = try {
            when (android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
            }
        } catch (_: Exception) { 0f }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        return bmp
    }

    fun jpeg(bmp: Bitmap, maxSide: Int = 1600, quality: Int = 85): ByteArray {
        val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val b = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }

    /** Ищет в снимке QR-коды и возвращает тексты всех найденных. */
    fun scanQr(bmp: Bitmap): List<String> {
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        fun attempt(b: Bitmap): String? {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val src = RGBLuminanceSource(b.width, b.height, px)
            for (bin in listOf(HybridBinarizer(src), GlobalHistogramBinarizer(src))) {
                try { return reader.decodeWithState(BinaryBitmap(bin)).text } catch (_: Exception) { } finally { reader.reset() }
            }
            return null
        }
        for (div in listOf(1, 2, 3)) {
            val b = if (div == 1) bmp else Bitmap.createScaledBitmap(bmp, bmp.width / div, bmp.height / div, true)
            if (b.width < 120) break
            attempt(b)?.let { return listOf(it) }
        }
        return emptyList()
    }
}
