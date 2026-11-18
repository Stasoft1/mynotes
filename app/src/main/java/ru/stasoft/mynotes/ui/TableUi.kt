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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.Span
import ru.stasoft.mynotes.data.TableBlock
import ru.stasoft.mynotes.data.parseCellNumber
import ru.stasoft.mynotes.data.tableSums

/** Состояние окна правки таблицы: [insert] — новая таблица в позиции курсора, иначе правка блока [start, end). */
class TableDraft(val start: Int, val end: Int, val rows: List<List<String>>, val insert: Boolean)

fun newTableRows(): List<List<String>> = listOf(
    listOf("Столбец 1", "Столбец 2", "Столбец 3"),
    listOf("", "", ""),
    listOf("", "", ""),
)

private fun fmt(v: Double): String =
    if (v == Math.floor(v) && Math.abs(v) < 1e12) String.format(java.util.Locale("ru"), "%,.0f", v).replace(' ', ' ')
    else String.format(java.util.Locale("ru"), "%,.2f", v).replace(' ', ' ')

private fun colWidth(rows: List<List<String>>, c: Int): Dp {
    val len = rows.maxOf { it.getOrElse(c) { "" }.length }
    return (len * 8 + 28).coerceIn(64, 200).dp
}

/** Таблица для просмотра: шапка, строки и (если есть числовые столбцы) строка итогов. */
@Composable
fun TableGrid(rows: List<List<String>>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (rows.isEmpty()) return
    val line = MaterialTheme.colorScheme.outlineVariant
    val sums = remember(rows) { tableSums(rows) }
    val hasSums = sums.any { it != null }
    val widths = remember(rows) { rows[0].indices.map { colWidth(rows, it) } }
    val nums = remember(rows) { sums.map { it != null } }
    Column(
        modifier.horizontalScroll(rememberScrollState()).border(1.dp, line).let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        rows.forEachIndexed { ri, r ->
            Row(Modifier.background(if (ri == 0) MaterialTheme.colorScheme.surfaceContainerHigh else if (ri % 2 == 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else androidx.compose.ui.graphics.Color.Transparent)) {
                r.forEachIndexed { ci, cell ->
                    Text(
                        cell,
                        Modifier.width(widths[ci]).padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (ri == 0) FontWeight.Bold else null,
                        textAlign = if (ri > 0 && nums.getOrElse(ci) { false }) TextAlign.End else TextAlign.Start,
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
                    fontWeight = FontWeight.Bold,
                    textAlign = if (v != null) TextAlign.End else TextAlign.Start,
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
            TableGrid(t.rows, Modifier.padding(vertical = 6.dp), onClick = { onTable(t) })
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

/** Окно правки таблицы: ячейки, добавление и удаление строк и столбцов, итоги по числовым столбцам. */
@Composable
fun TableEditDialog(draft: TableDraft, onDismiss: () -> Unit, onSave: (List<List<String>>) -> Unit, onDelete: (() -> Unit)?) {
    var rows by remember { mutableStateOf(draft.rows.ifEmpty { newTableRows() }) }
    val cols = rows[0].size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.insert) "Новая таблица" else "Таблица") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Row {
                        (0 until cols).forEach { c ->
                            Box(Modifier.width(120.dp), contentAlignment = Alignment.CenterEnd) {
                                if (cols > 1) IconButton(onClick = { rows = rows.map { r -> r.filterIndexed { i, _ -> i != c } } }) {
                                    Icon(Icons.Filled.Close, "Удалить столбец", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    rows.forEachIndexed { ri, r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            r.forEachIndexed { ci, cell ->
                                OutlinedTextField(
                                    value = cell,
                                    onValueChange = { v -> rows = rows.mapIndexed { i, rr -> if (i == ri) rr.mapIndexed { j, x -> if (j == ci) v else x } else rr } },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.let { if (ri == 0) it.copy(fontWeight = FontWeight.Bold) else it },
                                    modifier = Modifier.width(120.dp).padding(end = 4.dp, bottom = 4.dp),
                                )
                            }
                            if (rows.size > 1) IconButton(onClick = { rows = rows.filterIndexed { i, _ -> i != ri } }) {
                                Icon(Icons.Filled.Close, "Удалить строку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { rows = rows + listOf(List(cols) { "" }) }) { Text("+ строка") }
                    TextButton(onClick = { rows = rows.map { it + "" } }) { Text("+ столбец") }
                }
                val sums = tableSums(rows)
                if (sums.any { it != null }) Text(
                    "Итого: " + sums.mapIndexedNotNull { i, v -> v?.let { rows[0].getOrElse(i) { "" }.ifBlank { "столбец ${i + 1}" } + " — " + fmt(it) } }.joinToString("; "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Первая строка — шапка. Суммы считаются в столбцах, где все ячейки — числа.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rows) }) { Text("Готово") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить таблицу", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
