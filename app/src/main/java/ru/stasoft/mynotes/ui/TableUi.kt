package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stasoft.mynotes.data.CellSt
import ru.stasoft.mynotes.data.Span
import ru.stasoft.mynotes.data.TableBlock
import ru.stasoft.mynotes.data.encodeCell
import ru.stasoft.mynotes.data.parseCell
import ru.stasoft.mynotes.data.tableSums

/** Состояние окна правки таблицы: [insert] — новая таблица в позиции курсора, иначе правка блока [start, end). */
class TableDraft(val start: Int, val end: Int, val rows: List<List<String>>, val insert: Boolean, val aligns: List<Int> = emptyList())

fun newTableRows(): List<List<String>> = listOf(
    listOf("Столбец 1", "Столбец 2", "Столбец 3"),
    listOf("", "", ""),
    listOf("", "", ""),
)

private fun fmt(v: Double): String =
    if (v == Math.floor(v) && Math.abs(v) < 1e12) String.format(java.util.Locale("ru"), "%,.0f", v).replace(' ', ' ')
    else String.format(java.util.Locale("ru"), "%,.2f", v).replace(' ', ' ')

private val SIZES = listOf(14f, 12f, 18f, 22f)

private fun family(f: Int) = when (f) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.Default }

private fun colorOf(rgb: Int) = Color(0xFF000000.toInt() or rgb)

private fun colWidth(rows: List<List<String>>, c: Int, sum: Double? = null): Dp {
    var px = 0f
    rows.forEach { r ->
        val (st, t) = parseCell(r.getOrElse(c) { "" })
        px = maxOf(px, t.length * SIZES[st.size] * (if (st.b) 0.66f else 0.6f))
    }
    sum?.let { px = maxOf(px, fmt(it).length * 14f * 0.68f) }
    if (c == 0) px = maxOf(px, 5 * 14f * 0.66f)
    return (px + 24).coerceIn(64f, 260f).dp
}

private fun alignOf(aligns: List<Int>, c: Int, numeric: Boolean): TextAlign = when (aligns.getOrElse(c) { 0 }) {
    1 -> TextAlign.Start
    2 -> TextAlign.Center
    3 -> TextAlign.End
    else -> if (numeric) TextAlign.End else TextAlign.Start
}

private fun textStyleOf(st: CellSt, header: Boolean, dark: Boolean): TextStyle = TextStyle(
    fontSize = SIZES[st.size].sp,
    fontWeight = if (st.b || header) FontWeight.Bold else null,
    fontStyle = if (st.i) FontStyle.Italic else null,
    textDecoration = if (st.u) TextDecoration.Underline else null,
    fontFamily = family(st.font),
    color = if (st.color >= 0) colorOf(st.color) else if (st.bg >= 0) (if (colorOf(st.bg).luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White) else Color.Unspecified,
)

/** Таблица для просмотра: шапка, строки с оформлением ячеек и (если есть числовые столбцы) строка итогов. */
@Composable
fun TableGrid(rows: List<List<String>>, aligns: List<Int> = emptyList(), modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (rows.isEmpty()) return
    val line = MaterialTheme.colorScheme.outlineVariant
    val sums = remember(rows) { tableSums(rows) }
    val hasSums = sums.any { it != null }
    val widths = remember(rows) { rows[0].indices.map { colWidth(rows, it, sums.getOrNull(it)) } }
    val nums = remember(rows) { sums.map { it != null } }
    Column(
        modifier.horizontalScroll(rememberScrollState()).border(1.dp, line).let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        rows.forEachIndexed { ri, r ->
            val rowBg = if (ri == 0) MaterialTheme.colorScheme.surfaceContainerHigh else if (ri % 2 == 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color.Transparent
            Row(Modifier.background(rowBg)) {
                r.forEachIndexed { ci, raw ->
                    val (st, text) = parseCell(raw)
                    Text(
                        text,
                        Modifier.width(widths[ci]).let { if (st.bg >= 0) it.background(colorOf(st.bg)) else it }.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = textStyleOf(st, ri == 0, false),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = alignOf(aligns, ci, nums.getOrElse(ci) { false }),
                    )
                }
            }
        }
        if (hasSums) Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
            rows[0].indices.forEach { ci ->
                val v = sums.getOrNull(ci)
                Text(
                    v?.let { fmt(it) } ?: if (ci == 0) "Итого" else "",
                    Modifier.width(widths[ci]).padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (v != null) alignOf(aligns, ci, true) else TextAlign.Start,
                )
            }
        }
    }
}

private fun clipSpans(spans: List<Span>, from: Int, to: Int): List<Span> =
    spans.filter { it.end > from && it.start < to }
        .map { it.copy(start = maxOf(it.start, from) - from, end = minOf(it.end, to) - from) }

/** Просмотр заметки: текст, таблица, снова текст. Касание таблицы открывает её правку, касание текста — режим правки. */
@Composable
fun NotePreview(
    text: String,
    spans: List<Span>,
    tables: List<TableBlock>,
    onTable: (TableBlock) -> Unit,
    onEditText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        var pos = 0
        tables.forEach { t ->
            val to = maxOf(pos, t.start - 1)
            val sub = text.substring(pos, to)
            if (sub.isNotBlank()) Text(
                buildStyled(sub, clipSpans(spans, pos, to)),
                Modifier.fillMaxWidth().clickable { onEditText() }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            TableGrid(t.rows, t.aligns, Modifier.padding(vertical = 6.dp), onClick = { onTable(t) })
            pos = minOf(text.length, t.end + 1)
        }
        if (pos < text.length) {
            val sub = text.substring(pos)
            if (sub.isNotBlank()) Text(
                buildStyled(sub, clipSpans(spans, pos, text.length)),
                Modifier.fillMaxWidth().clickable { onEditText() }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "Коснитесь таблицы, чтобы изменить её, или текста — чтобы править заметку.",
            Modifier.fillMaxWidth().clickable { onEditText() }.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class Cell(val text: String, val st: CellSt)

/** Редактор таблицы: ячейки, строки и столбцы, выравнивание столбцов, шрифт, размер, цвет текста и фона для ячейки, строки или столбца. */
@Composable
fun TableEditDialog(draft: TableDraft, onDismiss: () -> Unit, onSave: (List<List<String>>, List<Int>) -> Unit, onDelete: (() -> Unit)?) {
    var cells by remember {
        mutableStateOf(draft.rows.ifEmpty { newTableRows() }.map { r -> r.map { raw -> parseCell(raw).let { Cell(it.second, it.first) } } })
    }
    val cols = cells[0].size
    var aligns by remember { mutableStateOf(List(cols) { draft.aligns.getOrElse(it) { 0 } }) }
    var sel by remember { mutableStateOf(0 to 0) }
    var scope by remember { mutableStateOf(0) } // 0 ячейка, 1 строка, 2 столбец
    var colorFor by remember { mutableStateOf(0) } // 0 нет, 1 текст, 2 фон
    val r0 = sel.first.coerceIn(0, cells.size - 1)
    val c0 = sel.second.coerceIn(0, cols - 1)

    fun targets(): List<Pair<Int, Int>> = when (scope) {
        1 -> (0 until cols).map { r0 to it }
        2 -> cells.indices.map { it to c0 }
        else -> listOf(r0 to c0)
    }
    fun style(f: (CellSt) -> CellSt) {
        val t = targets().toSet()
        cells = cells.mapIndexed { ri, r -> r.mapIndexed { ci, c -> if ((ri to ci) in t) Cell(c.text, f(c.st)) else c } }
    }
    fun toggle(get: (CellSt) -> Boolean, set: (CellSt, Boolean) -> CellSt) {
        val all = targets().all { (r, c) -> get(cells[r][c].st) }
        style { set(it, !all) }
    }
    val curSt = cells[r0][c0].st
    val summary = remember(cells) { tableSums(cells.map { r -> r.map { encodeCell(it.st, it.text) } }) }

    if (colorFor != 0) ColorPickerDialog(
        if (colorFor == 1) "Цвет текста" else "Цвет фона",
        onDismiss = { colorFor = 0 },
        onPick = { c ->
            val rgb = c?.let { it and 0xFFFFFF } ?: -1
            if (colorFor == 1) style { it.copy(color = rgb) } else style { it.copy(bg = rgb) }
            colorFor = 0
        },
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                    Text(if (draft.insert) "Новая таблица" else "Таблица", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { onSave(cells.map { r -> r.map { encodeCell(it.st, it.text) } }, aligns) }) { Text("Готово") }
                }
                // Панель оформления
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    @Composable
                    fun Tool(icon: ImageVector, desc: String, on: Boolean = false, onClick: () -> Unit) {
                        IconButton(onClick = onClick) {
                            Icon(icon, desc, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    val al = aligns.getOrElse(c0) { 0 }
                    Tool(Icons.Filled.FormatAlignLeft, "По левому краю", al == 1) { aligns = aligns.mapIndexed { i, a -> if (i == c0) 1 else a } }
                    Tool(Icons.Filled.FormatAlignCenter, "По центру", al == 2) { aligns = aligns.mapIndexed { i, a -> if (i == c0) 2 else a } }
                    Tool(Icons.Filled.FormatAlignRight, "По правому краю", al == 3) { aligns = aligns.mapIndexed { i, a -> if (i == c0) 3 else a } }
                    Tool(Icons.Filled.FormatBold, "Жирный", curSt.b) { toggle({ it.b }, { s, v -> s.copy(b = v) }) }
                    Tool(Icons.Filled.FormatItalic, "Курсив", curSt.i) { toggle({ it.i }, { s, v -> s.copy(i = v) }) }
                    Tool(Icons.Filled.FormatUnderlined, "Подчёркнутый", curSt.u) { toggle({ it.u }, { s, v -> s.copy(u = v) }) }
                    Tool(Icons.Filled.FormatColorText, "Цвет текста", curSt.color >= 0) { colorFor = 1 }
                    Tool(Icons.Filled.FormatColorFill, "Цвет фона", curSt.bg >= 0) { colorFor = 2 }
                    Tool(Icons.Filled.FormatSize, "Размер: " + listOf("обычный", "мелкий", "крупный", "очень крупный")[curSt.size], curSt.size != 0) {
                        val n = listOf(0, 2, 3, 1)[curSt.size] // обычный → крупный → очень крупный → мелкий → обычный
                        style { it.copy(size = n) }
                    }
                    Tool(Icons.Filled.TextFields, "Шрифт: " + listOf("обычный", "с засечками", "моноширинный")[curSt.font], curSt.font != 0) {
                        val n = (curSt.font + 1) % 3
                        style { it.copy(font = n) }
                    }
                    Tool(Icons.Filled.FormatClear, "Сбросить оформление") { style { CellSt() } }
                }
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Применять к:", style = MaterialTheme.typography.labelMedium)
                    listOf("Ячейке", "Строке", "Столбцу").forEachIndexed { i, n ->
                        FilterChip(selected = scope == i, onClick = { scope = i }, label = { Text(n) })
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Row {
                            (0 until cols).forEach { c ->
                                Box(Modifier.width(124.dp), contentAlignment = Alignment.CenterEnd) {
                                    if (cols > 1) IconButton(onClick = {
                                        cells = cells.map { r -> r.filterIndexed { i, _ -> i != c } }
                                        aligns = aligns.filterIndexed { i, _ -> i != c }
                                    }) { Icon(Icons.Filled.Close, "Удалить столбец", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                        cells.forEachIndexed { ri, r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                r.forEachIndexed { ci, cell ->
                                    val st = cell.st
                                    val bg = if (st.bg >= 0) colorOf(st.bg) else Color.Transparent
                                    OutlinedTextField(
                                        value = cell.text,
                                        onValueChange = { v -> cells = cells.mapIndexed { i, rr -> if (i == ri) rr.mapIndexed { j, x -> if (j == ci) Cell(v, x.st) else x } else rr } },
                                        singleLine = true,
                                        textStyle = textStyleOf(st, ri == 0, false).copy(
                                            textAlign = when (aligns.getOrElse(ci) { 0 }) { 2 -> TextAlign.Center; 3 -> TextAlign.End; else -> TextAlign.Start },
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = bg, unfocusedContainerColor = bg),
                                        modifier = Modifier.width(120.dp).padding(end = 4.dp, bottom = 4.dp).onFocusChanged { if (it.isFocused) sel = ri to ci },
                                    )
                                }
                                if (cells.size > 1) IconButton(onClick = { cells = cells.filterIndexed { i, _ -> i != ri } }) {
                                    Icon(Icons.Filled.Close, "Удалить строку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { cells = cells + listOf(List(cols) { Cell("", CellSt()) }) }) { Text("+ строка") }
                        TextButton(onClick = { cells = cells.map { it + Cell("", CellSt()) }; aligns = aligns + 0 }) { Text("+ столбец") }
                    }
                    if (summary.any { it != null }) Text(
                        "Итого: " + summary.mapIndexedNotNull { i, v -> v?.let { cells[0].getOrNull(i)?.text.orEmpty().ifBlank { "столбец ${i + 1}" } + " — " + fmt(it) } }.joinToString("; "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Коснитесь ячейки и выберите оформление. Выравнивание относится ко всему столбцу. Первая строка — шапка; суммы считаются в столбцах, где все ячейки — числа.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
