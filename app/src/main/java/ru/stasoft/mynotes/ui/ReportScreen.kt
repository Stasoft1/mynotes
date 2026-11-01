package ru.stasoft.mynotes.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stasoft.mynotes.data.evalFormula
import ru.stasoft.mynotes.data.formatNumber
import ru.stasoft.mynotes.data.resolveTitle

private class Calc(val name: String, val expr: String)

private fun loadCalcs(ctx: Context, folderId: Long?): List<Calc> {
    val raw = ctx.getSharedPreferences("report", Context.MODE_PRIVATE).getString("calc_${folderId ?: 0}", "") ?: ""
    return raw.lines().mapNotNull { l ->
        val i = l.indexOf('=')
        if (i <= 0) null else Calc(l.substring(0, i).trim(), l.substring(i + 1).trim())
    }
}

private fun saveCalcs(ctx: Context, folderId: Long?, list: List<Calc>) {
    ctx.getSharedPreferences("report", Context.MODE_PRIVATE).edit()
        .putString("calc_${folderId ?: 0}", list.joinToString("\n") { "${it.name}=${it.expr}" }).apply()
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("report", Context.MODE_PRIVATE)

/**
 * Отчёт по папке. Строки — записи выбранного уровня вложенности (или все заметки), столбцы — любые из переменных
 * (показать/скрыть), плюс свои расчётные столбцы; сортировка по нажатию на заголовок. Настройки помнятся для папки.
 */
@Composable
fun ReportDialog(vm: AppViewModel, folderId: Long?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val key = folderId ?: 0L
    val numbers by vm.numbers.collectAsState()
    var folderTitle by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf<List<AppViewModel.ReportRow>>(emptyList()) }
    var calcs by remember { mutableStateOf(loadCalcs(ctx, folderId)) }
    var adding by remember { mutableStateOf(false) }
    var pickCols by remember { mutableStateOf(false) }
    var levelMenu by remember { mutableStateOf(false) }
    // level: 1.. — глубина, 0 — все заметки.
    var level by remember { mutableStateOf(prefs(ctx).getInt("level_$key", 1)) }
    var hidden by remember { mutableStateOf((prefs(ctx).getString("hide_$key", "") ?: "").split("\u0001").filter { it.isNotEmpty() }.toSet()) }
    var sortCol by remember { mutableStateOf<String?>(null) }
    var desc by remember { mutableStateOf(false) }
    LaunchedEffect(folderId) {
        val (t, r) = vm.reportData(folderId)
        folderTitle = t
        all = r
    }
    val maxDepth = all.maxOfOrNull { it.depth } ?: 1
    val lv = if (level > maxDepth) maxDepth else level
    val picked = if (lv == 0) all.filter { !it.isFolder } else all.filter { it.depth == lv }
    val shown = picked.filter { !numbers[it.id].isNullOrEmpty() }
    val allNames = LinkedHashSet<String>().apply {
        shown.forEach { r -> numbers[r.id]?.forEach { add(it.first) } }
        folderId?.let { id -> numbers[id]?.forEach { add(it.first) } }
    }.toList()
    val names = allNames.filter { it !in hidden }
    fun valuesOf(id: Long?): Map<String, Double> = id?.let { numbers[it] }.orEmpty().associate { it.first to it.second }
    fun cell(values: Map<String, Double>, name: String): String = values[name]?.let { formatNumber(it) } ?: ""
    fun calcValue(values: Map<String, Double>, c: Calc): Double? =
        evalFormula(c.expr) { nm -> values.entries.firstOrNull { it.key.equals(nm, ignoreCase = true) }?.value }
    fun calcCell(values: Map<String, Double>, c: Calc): String = calcValue(values, c)?.let { formatNumber(it) } ?: ""
    val header = names.map { it.ifEmpty { "Число" } } + calcs.map { it.name }
    val headerKeys = names + calcs.map { it.name }
    var body = shown.map { r ->
        val v = valuesOf(r.id)
        val base = resolveTitle(r.title.ifBlank { "Без названия" }, numbers[r.id])
        val title = if (lv != 1 && r.parentTitle.isNotBlank()) resolveTitle(r.parentTitle, null) + " › " + base else base
        val nums = names.map { v[it] } + calcs.map { calcValue(v, it) }
        Triple(title, nums, names.map { cell(v, it) } + calcs.map { calcCell(v, it) })
    }
    sortCol?.let { col ->
        val i = headerKeys.indexOf(col)
        if (i >= 0) body = if (desc) body.sortedByDescending { it.second[i] ?: Double.NEGATIVE_INFINITY } else body.sortedBy { it.second[i] ?: Double.POSITIVE_INFINITY }
    }
    val totalValues = valuesOf(folderId)
    val total = if (folderId != null && totalValues.isNotEmpty()) names.map { cell(totalValues, it) } + calcs.map { calcCell(totalValues, it) } else null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Отчёт: " + (folderTitle?.let { resolveTitle(it, numbers[folderId]) } ?: "вся база"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Box {
                        TextButton(onClick = { levelMenu = true }) {
                            Text(if (lv == 0) "Строки: все заметки" else "Строки: уровень $lv")
                        }
                        DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                            (1..maxDepth).forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(if (d == 1) "Уровень 1 (прямое содержимое)" else "Уровень $d") },
                                    onClick = { levelMenu = false; level = d; prefs(ctx).edit().putInt("level_$key", d).apply() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Все заметки") },
                                onClick = { levelMenu = false; level = 0; prefs(ctx).edit().putInt("level_$key", 0).apply() },
                            )
                        }
                    }
                    TextButton(onClick = { pickCols = true }) { Text("Столбцы…") }
                }
                if (body.isEmpty()) {
                    Text(
                        "В этой папке нет записей со значениями переменных. Объявите переменные в свойствах папки и вписывайте их в заметки.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(scroll)) {
                    LazyColumn {
                        item {
                            Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                ReportCell("", 150, bold = true)
                                header.forEachIndexed { i, h ->
                                    val k = headerKeys.getOrNull(i)
                                    val mark = if (sortCol == k) (if (desc) " ▼" else " ▲") else ""
                                    Box(Modifier.clickable {
                                        if (sortCol == k) { if (desc) { sortCol = null; desc = false } else desc = true } else { sortCol = k; desc = false }
                                    }) { ReportCell(h + mark, 96, bold = true, end = true) }
                                }
                            }
                        }
                        items(body) { (title, _, cells) ->
                            Row {
                                ReportCell(title, 150)
                                cells.forEach { ReportCell(it, 96, end = true) }
                            }
                        }
                        if (total != null) item {
                            Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                                ReportCell("Итого", 150, bold = true)
                                total.forEach { ReportCell(it, 96, bold = true, end = true) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = { adding = true }) { Text("+ Расчётный столбец") }
                    if (calcs.isNotEmpty()) TextButton(onClick = {
                        calcs = calcs.dropLast(1)
                        saveCalcs(ctx, folderId, calcs)
                    }) { Text("Убрать «${calcs.last().name}»") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextButton(onClick = {
                        val sb = StringBuilder()
                        sb.append(("Запись" + "\t" + header.joinToString("\t")).trimEnd()).append('\n')
                        body.forEach { (t, _, c) -> sb.append(t).append('\t').append(c.joinToString("\t")).append('\n') }
                        total?.let { sb.append("Итого\t").append(it.joinToString("\t")).append('\n') }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, sb.toString())
                        }
                        ctx.startActivity(Intent.createChooser(send, "Отчёт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Поделиться") }
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
    if (pickCols) {
        AlertDialog(
            onDismissRequest = { pickCols = false },
            title = { Text("Столбцы отчёта") },
            text = {
                Column {
                    allNames.forEach { n ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                hidden = if (n in hidden) hidden - n else hidden + n
                                prefs(ctx).edit().putString("hide_$key", hidden.joinToString("\u0001")).apply()
                            },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = n !in hidden, onCheckedChange = null)
                            Text(n.ifEmpty { "Число (первая строка)" }, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCols = false }) { Text("Готово") } },
        )
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var expr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Расчётный столбец") },
            text = {
                Column {
                    Text(
                        "Считается по столбцам отчёта в каждой строке и в итоге. Например: Цена литра = СуммаБ / Литры или Пробег на литр = {За период} / Литры.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    TextField(value = expr, onValueChange = { expr = it }, label = { Text("Формула") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && expr.isNotBlank(), onClick = {
                    calcs = calcs + Calc(name.trim(), expr.trim())
                    saveCalcs(ctx, folderId, calcs)
                    adding = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ReportCell(text: String, widthDp: Int, bold: Boolean = false, end: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(widthDp.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else null,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
