package ru.stasoft.mynotes.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Ссылка или телефон, найденные в тексте заметки. [kind]: 0 — ссылка, 1 — телефон. */
data class LinkHit(val kind: Int, val target: String, val shown: String, val line: String)

private val URL_RE = Regex(
    "(?i)(?:https?://|www\\.)[^\\s<>\"')]+" +
        "|(?<![@\\w.-])[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:ru|su|com|net|org|рф|io|info|me|ua|by|kz|xyz|online|site|app)\\b(?:/[^\\s<>\"')]*)?",
)
private val PHONE_RE = Regex("(?<!\\d)(?:\\+?[78][\\s(-]*)?\\(?\\d{3}\\)?[\\s-]*\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}(?!\\d)")

private fun normalizePhone(raw: String): String? {
    val d = raw.filter { it.isDigit() }
    val core = when {
        d.length == 11 && (d[0] == '7' || d[0] == '8') -> d.substring(1)
        d.length == 10 -> d
        else -> return null
    }
    return "+7$core"
}

private fun prettyPhone(p: String): String {
    val c = p.removePrefix("+7")
    return "+7 (${c.substring(0, 3)}) ${c.substring(3, 6)}-${c.substring(6, 8)}-${c.substring(8, 10)}"
}

/** Ссылки и телефоны из текста (до 8 штук, без повторов, в порядке появления). */
fun findLinks(body: String): List<LinkHit> {
    if (body.isEmpty()) return emptyList()
    val out = LinkedHashMap<String, LinkHit>()
    for (line in body.lineSequence()) {
        if (out.size >= 8) break
        val l = line.trim()
        if (l.isEmpty()) continue
        for (m in URL_RE.findAll(l)) {
            val url = m.value.trimEnd('.', ',', ';', ':', '!', '?')
            if (url.length < 4) continue
            val target = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
            out.putIfAbsent("u$target", LinkHit(0, target, url, l))
        }
        for (m in PHONE_RE.findAll(l)) {
            val p = normalizePhone(m.value) ?: continue
            out.putIfAbsent("p$p", LinkHit(1, p, prettyPhone(p), l))
        }
    }
    return out.values.toList().take(8)
}

private val ADDR_TITLE = Regex("^\\p{L}[\\p{L}. -]*\\s\\d+\\p{L}?(?:-\\d+){0,2}$")
private val ADDR_PARTS = Regex("^(.*?)\\s(\\d+\\p{L}?)(?:-(\\d+))?(?:-(\\d+))?$")

/** Адрес для маршрута: строка «Адрес:» в тексте или название вида «Улица 30-148» (квартира отбрасывается, корпус сохраняется). */
fun findAddress(title: String, body: String): String? {
    val line = body.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("Адрес:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
    val raw = line ?: title.trim().takeIf { ADDR_TITLE.matches(it) } ?: return null
    val m = ADDR_PARTS.find(raw) ?: return raw
    val corp = if (m.groupValues[4].isNotEmpty()) " к" + m.groupValues[3] else ""
    return m.groupValues[1].trim() + " " + m.groupValues[2] + corp
}

private const val DEFAULT_CITY = "Санкт-Петербург"

fun routeCity(ctx: Context): String = ctx.getSharedPreferences("ui", 0).getString("route_city", DEFAULT_CITY) ?: DEFAULT_CITY

/** Открывает адрес в картах (на выбор: Яндекс Карты, Навигатор, 2ГИС, Google Карты и т.д.). */
fun openRoute(ctx: Context, address: String) {
    val q = (routeCity(ctx).takeIf { it.isNotBlank() }?.let { "$it, " } ?: "") + address
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))
    try {
        ctx.startActivity(Intent.createChooser(intent, "Маршрут").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Нет приложения с картами", Toast.LENGTH_SHORT).show()
    }
}

private fun openHit(ctx: Context, hit: LinkHit) {
    val intent = if (hit.kind == 0) Intent(Intent.ACTION_VIEW, Uri.parse(hit.target))
    else Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(hit.target)))
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Не удалось открыть", Toast.LENGTH_SHORT).show()
    }
}

/** Кнопки на строке заметки: ссылка и/или телефон. Нажатие показывает список и открывает выбранное. */
@Composable
fun LinkButtons(title: String, body: String) {
    val ctx = LocalContext.current
    val hits = remember(body) { findLinks(body) }
    // 0 — окно закрыто, 1 — ссылки, 2 — телефоны.
    var open by remember { mutableIntStateOf(0) }
    val addr = remember(title, body) { findAddress(title, body) }.takeIf { ru.stasoft.mynotes.modules.Modules.on("route") }
    if (addr != null) RouteButton { openRoute(ctx, addr) }
    val hasLinks = hits.any { it.kind == 0 }
    val hasPhones = hits.any { it.kind == 1 }
    if (hasLinks) HitButton(true) { open = 1 }
    if (hasPhones) HitButton(false) { open = 2 }
    if (open != 0) {
        val kind = if (open == 1) 0 else 1
        val list = hits.filter { it.kind == kind }
        AlertDialog(
            onDismissRequest = { open = 0 },
            title = { Text(title.ifBlank { "Без названия" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    list.forEach { h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = 0
                                    openHit(ctx, h)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (h.kind == 0) Icons.Filled.Link else Icons.Filled.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(h.shown, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                if (h.line != h.shown) {
                                    Text(
                                        h.line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = 0 }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun RouteButton(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Directions, contentDescription = "Маршрут", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/** Настройка: город, который добавляется к адресу при построении маршрута. */
@Composable
fun RouteSettingsSection() {
    val ctx = LocalContext.current
    var city by remember { mutableStateOf(routeCity(ctx)) }
    var edit by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Text(
        "Маршрут к адресу",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        "Город для адресов без города: ${city.ifBlank { "не задан" }}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    TextButton(onClick = { text = city; edit = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Изменить город…") }
    if (edit) {
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("Город") },
            text = { androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    city = text.trim()
                    ctx.getSharedPreferences("ui", 0).edit().putString("route_city", city).apply()
                    edit = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HitButton(link: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (link) Icons.Filled.Link else Icons.Filled.Phone,
            contentDescription = if (link) "Ссылки в заметке" else "Телефоны в заметке",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}
