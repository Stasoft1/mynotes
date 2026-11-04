package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.AGG_LABELS
import ru.stasoft.mynotes.data.VarDef
import ru.stasoft.mynotes.data.varLabel

/** Строка списка переменных: действующее определение, откуда пришла, отключена ли здесь, переопределена ли. */
private data class VarRow(val def: VarDef, val from: String?, val disabled: Boolean, val overridden: Boolean)

private fun VarDef.summary(): String {
    val parts = mutableListOf(AGG_LABELS[agg] ?: agg)
    if (formula.isNotBlank()) parts.add("= $formula")
    else if (field.isNotEmpty()) parts.add("поле «$field»")
    if (hideZero) parts.add("без нуля")
    return parts.joinToString(" · ")
}

/** Переменные папки: карточки с краткой сводкой, настройка каждой — в своём окне. */
@Composable
fun VarsSection(
    inherited: List<Pair<VarDef, String>>,
    own: List<VarDef>,
    onChange: (List<VarDef>) -> Unit,
) {
    var editing by remember { mutableStateOf<VarRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(false) }
    val inheritedNames = inherited.map { it.first.name }.toSet()
    val rows = buildList {
        inherited.forEach { (def, from) ->
            val ov = own.firstOrNull { it.name == def.name }
            val disabled = ov?.off == true
            val eff = if (ov != null && !ov.off) ov else def
            add(VarRow(eff, from, disabled, ov != null && !ov.off))
        }
        own.filter { !it.off && it.name !in inheritedNames }.forEach { add(VarRow(it, null, false, false)) }
    }
    val allNames = rows.filter { !it.disabled }.map { it.def.name }.filter { it.isNotEmpty() }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "ПЕРЕМЕННЫЕ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Поле «Имя: число» в тексте заметок. Действует в этой папке и во всех вложенных, значения сводятся вверх " +
                "по дереву. Нажмите на переменную, чтобы настроить её.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (rows.isEmpty()) {
            Text("Переменных пока нет.", modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { row ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 3.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (row.disabled) 0.5f else 1f).clickable { editing = row },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(varLabel(row.def.name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (row.disabled) "Отключена в этой папке" else row.def.summary(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (row.from != null) {
                            Text(
                                "из папки «${row.from}»" + if (row.overridden) " · изменена здесь" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }) { Text("+ Переменная") }
            androidx.compose.foundation.layout.Box {
                FilledTonalButton(onClick = { presets = true }) { Text("Готовый набор ▾") }
                DropdownMenu(expanded = presets, onDismissRequest = { presets = false }) {
                    DropdownMenuItem(text = { Text("Заявки: Сумма, Пробег, За период") }, onClick = {
                        presets = false
                        val have = own.map { it.name }.toSet() + inheritedNames
                        onChange(
                            own + listOf(
                                VarDef("Сумма", "sum"),
                                VarDef("Пробег", "max"),
                                VarDef("За период", "span", field = "Пробег"),
                            ).filter { it.name !in have },
                        )
                    })
                    DropdownMenuItem(text = { Text("Кабель: расход, взял, остаток") }, onClick = {
                        presets = false
                        val have = own.map { it.name }.toSet() + inheritedNames
                        onChange(
                            own + listOf(
                                VarDef("Кабель вп", "sum"),
                                VarDef("Кабель кокс", "sum"),
                                VarDef("Взял вп", "sum"),
                                VarDef("Взял кокс", "sum"),
                                VarDef("Остаток вп", "balance", formula = "{Взял вп} - {Кабель вп}"),
                                VarDef("Остаток кокс", "balance", formula = "{Взял кокс} - {Кабель кокс}"),
                            ).filter { it.name !in have },
                        )
                    })
                }
            }
        }
    }

    editing?.let { row ->
        VarEditDialog(
            initial = row.def,
            isNew = false,
            fromLabel = row.from,
            disabled = row.disabled,
            otherNames = allNames.filter { it != row.def.name },
            onDismiss = { editing = null },
            onSave = { def ->
                editing = null
                val rest = own.filterNot { it.name == def.name }
                val original = inherited.firstOrNull { it.first.name == def.name }?.first
                onChange(if (original != null && original == def) rest else rest + def)
            },
            onRemove = {
                editing = null
                if (row.from == null) onChange(own.filterNot { it.name == row.def.name })
                else onChange(own.filterNot { it.name == row.def.name } + VarDef(row.def.name, row.def.agg, off = true))
            },
            onRestore = {
                editing = null
                onChange(own.filterNot { it.name == row.def.name })
            },
        )
    }
    if (adding) {
        VarEditDialog(
            initial = VarDef("", "sum"),
            isNew = true,
            fromLabel = null,
            disabled = false,
            otherNames = allNames,
            onDismiss = { adding = false },
            onSave = { def ->
                adding = false
                onChange(own.filterNot { it.name == def.name } + def)
            },
            onRemove = {},
            onRestore = {},
        )
    }
}

@Composable
private fun AggDropdown(agg: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text((AGG_LABELS[agg] ?: agg) + "  ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AGG_LABELS.forEach { (k, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(k) })
            }
        }
    }
}

private fun appendToken(formula: String, token: String): String {
    val sep = if (formula.isBlank() || formula.endsWith(" ") || formula.endsWith("(")) "" else " "
    return formula + sep + token + " "
}

@Composable
private fun VarEditDialog(
    initial: VarDef,
    isNew: Boolean,
    fromLabel: String?,
    disabled: Boolean,
    otherNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (VarDef) -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
) {
    var firstLine by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initial.name) }
    var field by remember { mutableStateOf(initial.field) }
    var agg by remember { mutableStateOf(initial.agg) }
    var formula by remember { mutableStateOf(initial.formula) }
    var hideZero by remember { mutableStateOf(initial.hideZero) }
    var pickVar by remember { mutableStateOf(false) }
    val needsFormula = agg == "calc" || agg == "balance"
    val canFormula = agg != "span" && agg != "none" && !firstLine
    val valid = (firstLine || name.isNotBlank() || !isNew && initial.name.isEmpty()) && (!needsFormula || formula.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNew) "Новая переменная" else varLabel(initial.name))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fromLabel != null) {
                    Text(
                        "Объявлена в папке «$fromLabel». Изменения действуют только в этой папке и вложенных.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isNew) {
                    Row(Modifier.fillMaxWidth().clickable { firstLine = !firstLine }, verticalAlignment = Alignment.CenterVertically) {
                        AppCheckbox(checked = firstLine, onCheckedChange = { firstLine = it })
                        Text("Число в первой строке заметки")
                    }
                    if (!firstLine) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название, например Сумма") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (!firstLine && initial.name.isNotEmpty() || isNew && !firstLine) {
                    TextField(
                        value = field,
                        onValueChange = { field = it },
                        label = { Text("Поле в тексте, если называется иначе") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Как считать итог по папке", style = MaterialTheme.typography.labelLarge)
                AggDropdown(agg) { agg = it }
                if (agg == "calc") {
                    Text(
                        "Формула по итогам этой же строки: например СуммаБ ÷ Литры даст среднюю цену.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (agg == "balance") {
                    Text(
                        "Формула считается для каждой заметки; остаток накапливается по датам в названиях папок дней.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canFormula) {
                    TextField(
                        value = formula,
                        onValueChange = { formula = it },
                        label = { Text(if (needsFormula) "Формула" else "Формула (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("+", "−", "×", "÷", "(", ")").forEach { op ->
                            FilledTonalButton(
                                onClick = { formula = appendToken(formula, op) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text(op) }
                        }
                    }
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { pickVar = true }, enabled = otherNames.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Вставить переменную  ▾")
                        }
                        DropdownMenu(expanded = pickVar, onDismissRequest = { pickVar = false }) {
                            otherNames.forEach { n ->
                                DropdownMenuItem(text = { Text(n) }, onClick = { pickVar = false; formula = appendToken(formula, "{$n}") })
                            }
                        }
                    }
                    if (formula.isNotEmpty()) {
                        TextButton(onClick = { formula = "" }) { Text("Очистить формулу") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Скрывать, если 0", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Не показывать под названием, когда значение равно нулю",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppSwitch(checked = hideZero, onCheckedChange = { hideZero = it })
                }
                if (!isNew) {
                    Spacer(Modifier.height(4.dp))
                    if (disabled) {
                        TextButton(onClick = onRestore) { Text("Вернуть в этой папке") }
                    } else {
                        TextButton(onClick = onRemove) {
                            Text(if (fromLabel == null) "Удалить переменную" else "Отключить в этой папке")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !disabled,
                onClick = {
                    onSave(
                        VarDef(
                            name = if (firstLine) "" else name.trim().trimEnd(':'),
                            agg = agg,
                            off = false,
                            field = if (firstLine) "" else field.trim().trimEnd(':'),
                            formula = if (canFormula) formula.trim() else "",
                            hideZero = hideZero,
                        ),
                    )
                },
            ) { Text(if (isNew) "Добавить" else "Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
