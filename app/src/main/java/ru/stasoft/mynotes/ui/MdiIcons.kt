package ru.stasoft.mynotes.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import ru.stasoft.mynotes.data.NoteEntity

/**
 * Набор Material Design Icons (Pictogrammers, лицензия Apache 2.0): около 7400 иконок по именам, как в MyTreeNotes
 * (например «stethoscope»). Контуры лежат в assets/mdi_paths.txt и читаются один раз при первом показе иконки.
 */
object Mdi {
    @Volatile private var paths: Map<String, String>? = null
    private val parsed = HashMap<String, Path>()

    private fun load(ctx: Context): Map<String, String> {
        paths?.let { return it }
        synchronized(this) {
            paths?.let { return it }
            val m = HashMap<String, String>(9000)
            try {
                ctx.applicationContext.assets.open("mdi_paths.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val i = line.indexOf('\t')
                        if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
                    }
                }
            } catch (_: Exception) {
            }
            paths = m
            return m
        }
    }

    fun has(ctx: Context, name: String): Boolean = load(ctx).containsKey(name)

    fun names(ctx: Context): List<String> = load(ctx).keys.sorted()

    fun path(ctx: Context, name: String): Path? {
        val d = load(ctx)[name] ?: return null
        synchronized(parsed) {
            parsed[name]?.let { return it }
            return try {
                PathParser().parsePathString(d).toPath().also { parsed[name] = it }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Часто нужные иконки: показываются в окне выбора, пока поиск пуст. */
val MDI_POPULAR = listOf(
    "home", "office-building", "city", "factory", "warehouse", "store", "hospital-building", "school", "bank",
    "currency-rub", "cash", "wallet", "credit-card", "cart", "tag", "receipt",
    "car", "truck", "bus", "train", "airplane", "bike", "motorbike", "gas-station", "car-wrench", "car-battery",
    "wrench", "hammer", "screwdriver", "toolbox", "pipe-wrench", "flash", "lightbulb", "cog", "ladder",
    "phone", "cellphone", "laptop", "monitor", "television", "router-wireless", "wifi", "ethernet-cable", "lan",
    "server", "harddisk", "sim", "satellite-variant", "antenna", "printer", "camera", "video",
    "email", "message-text", "bell", "calendar", "clock", "alarm", "star", "heart", "flag", "bookmark",
    "lock", "key", "shield-check", "check-circle", "information", "alert", "help-circle",
    "account", "account-group", "baby-face", "paw", "cake-variant", "food", "coffee", "glass-cocktail", "pizza",
    "stethoscope", "hospital", "pill", "heart-pulse", "dumbbell", "bicycle", "weather-sunny", "snowflake", "tree",
    "book-open-variant", "file-document", "note-text", "clipboard-check", "format-list-checks", "map-marker", "earth",
    "briefcase", "scale-balance", "calculator", "music", "headphones", "gamepad-variant", "gift", "airballoon",
)

@Composable
fun MdiIcon(name: String, tint: Color, size: Dp = 28.dp, modifier: Modifier = Modifier): Boolean {
    val ctx = LocalContext.current
    val path = remember(name) { Mdi.path(ctx, name) } ?: return false
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, tint)
        }
    }
    return true
}

/** Иконка записи: имя из набора MDI, старый ключ из первой версии каталога или значок по умолчанию (false). */
@Composable
fun NoteIcon(key: String?, tint: Color, size: Dp = 28.dp): Boolean {
    if (key.isNullOrEmpty()) return false
    if (MdiIcon(key, tint, size)) return true
    val vec = iconFor(key) ?: return false
    Icon(vec, null, tint = tint, modifier = Modifier.size(size))
    return true
}

private fun collect(node: Any?, keyPath: String, out: MutableList<Pair<String, String>>) {
    when (node) {
        is JSONObject -> node.keys().forEach { k -> collect(node.opt(k), "$keyPath/$k", out) }
        is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), keyPath, out)
        is String -> out.add(keyPath to node)
    }
}

/**
 * Иконка, привязанная к записи в MyTreeNotes: имя иконки лежит в служебном поле map. Формат поля точно неизвестен,
 * поэтому ищем среди его строк имя, которое есть в наборе (сначала в полях со словом icon).
 */
fun legacyIconName(ctx: Context, note: NoteEntity): String? {
    val raw = note.mapJson.trim()
    if (raw.isEmpty()) return null
    try {
        val first = JSONObject(raw).optJSONArray("icon")?.optString(0, "").orEmpty()
        if (first.isNotEmpty() && Mdi.has(ctx, first)) return first
    } catch (_: Exception) {
    }
    val pairs = mutableListOf<Pair<String, String>>()
    try {
        collect(if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw), "", pairs)
    } catch (_: Exception) {
        return null
    }
    val named = pairs.filter { (k, _) -> k.contains("icon", ignoreCase = true) }.map { it.second }
    return (named + pairs.map { it.second }).firstOrNull { it.length > 2 && Mdi.has(ctx, it) }
}

/** Что показывать у записи: свой выбор (пустая строка — «без иконки»), иначе иконка из импорта. */
@Composable
fun effectiveIcon(note: NoteEntity): String? {
    val ctx = LocalContext.current
    return remember(note.id, note.iconKey, note.mapJson) {
        when (note.iconKey) {
            null -> legacyIconName(ctx, note)
            "" -> null
            else -> note.iconKey
        }
    }
}

/** Цвет иконки из импорта: map = {"icon":["monitor","#03A9F4"]}. Применяется только к иконке из импорта, не к выбранной вручную. */
fun legacyIconColor(note: NoteEntity): Color? {
    if (note.iconKey != null) return null
    return try {
        val arr = JSONObject(note.mapJson.trim()).optJSONArray("icon") ?: return null
        val hex = arr.optString(1, "")
        if (hex.startsWith("#") && (hex.length == 7 || hex.length == 9)) {
            Color(android.graphics.Color.parseColor(hex))
        } else null
    } catch (_: Exception) {
        null
    }
}
