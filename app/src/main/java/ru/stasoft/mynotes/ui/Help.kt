package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stasoft.mynotes.modules.Modules

/** Общие разделы справки (не модули). Тексты лежат в assets/help/<id>.md. */
val HELP_BASICS = listOf(
    "vars" to "Переменные и подсчёты",
    "links" to "Ссылки между заметками",
    "templates" to "Шаблоны",
    "voice" to "Голосовой ввод",
)

fun helpTitle(id: String): String =
    HELP_BASICS.firstOrNull { it.first == id }?.second ?: Modules.all.firstOrNull { it.id == id }?.title ?: id

private fun loadHelp(ctx: android.content.Context, id: String): String =
    try { ctx.assets.open("help/$id.md").bufferedReader(Charsets.UTF_8).use { it.readText() } } catch (_: Exception) { "Справка для этого раздела пока не написана." }

/** Разметка справки: **жирный** и `код` внутри строки. */
private fun inline(s: String, codeBg: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val re = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var i = 0
    for (m in re.findAll(s)) {
        append(s.substring(i, m.range.first))
        if (m.groups[1] != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(m.groupValues[2]) }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

@Composable
private fun HelpBody(text: String) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val lines = text.lines()
    var i = 0
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        while (i < lines.size) {
            val l = lines[i]
            when {
                l.startsWith("# ") -> {}
                l.startsWith("## ") -> Text(l.removePrefix("## "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                l.startsWith("```") -> {
                    val sb = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(lines[i]); i++ }
                    Text(
                        sb.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(codeBg).horizontalScroll(rememberScrollState()).padding(10.dp),
                    )
                }
                l.startsWith("- ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(inline(l.removePrefix("- "), codeBg), style = MaterialTheme.typography.bodyMedium)
                }
                l.startsWith("> ") -> Text(inline(l.removePrefix("> "), codeBg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp))
                l.startsWith("|") -> {
                    val cells = l.trim().trim('|').split('|').map { it.trim() }
                    if (!cells.all { c -> c.all { it == '-' || it == ':' } }) Text(inline("**" + cells.first() + "** — " + cells.drop(1).joinToString(" · "), codeBg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
                l.isBlank() -> {}
                else -> Text(inline(l, codeBg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            i++
        }
    }
}

/** Полноэкранная справка по разделу [id]: модуль или общий раздел. */
@Composable
fun HelpDialog(id: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember(id) { loadHelp(ctx, id) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text(helpTitle(id), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                    Text(
                        text.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ").orEmpty(),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HelpBody(text)
                }
            }
        }
    }
}

/** Кнопка «?» для заголовка окна модуля. */
@Composable
fun HelpButton(id: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("?") }
    if (open) HelpDialog(id) { open = false }
}

/** Ссылка «Как пользоваться» на странице настроек или в каталоге модулей. */
@Composable
fun HelpLink(id: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Text(
        "Как пользоваться",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clickable { open = true }.padding(vertical = 4.dp),
    )
    if (open) HelpDialog(id) { open = false }
}

/** Страница настроек «Справка»: общие разделы и все модули. */
@Composable
fun HelpIndexSection(vm: AppViewModel) {
    var open by remember { mutableStateOf<String?>(null) }
    open?.let { HelpDialog(it) { open = null } }
    @Composable fun item(id: String, title: String, sub: String) {
        Column(Modifier.fillMaxWidth().clickable { open = id }.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (ru.stasoft.mynotes.data.GUIDE_ENABLED) {
        val ctxG = LocalContext.current
        Column(
            Modifier.fillMaxWidth().clickable {
                vm.addGuide { created ->
                    android.widget.Toast.makeText(
                        ctxG,
                        if (created) "Папка «Руководство» добавлена на главный экран" else "Папка «Руководство» уже есть в базе",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Руководство в виде заметок", style = MaterialTheme.typography.titleMedium)
            Text("Добавить на главный экран папку с заметками и переходами между ними", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text("ОСНОВЫ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    HELP_BASICS.forEach { (id, t) -> item(id, t, "") }
    ru.stasoft.mynotes.modules.MODULE_CATEGORIES.forEach { cat ->
        val list = Modules.all.filter { it.category == cat }
        if (list.isEmpty()) return@forEach
        Text(cat.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        list.forEach { m -> item(m.id, m.title, "") }
    }
}
