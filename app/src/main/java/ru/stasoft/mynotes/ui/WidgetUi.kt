package ru.stasoft.mynotes.ui

import android.appwidget.AppWidgetManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle
import ru.stasoft.mynotes.widgets.QuickBtn
import ru.stasoft.mynotes.widgets.WidgetCfg
import ru.stasoft.mynotes.widgets.WidgetPrefs
import ru.stasoft.mynotes.widgets.WidgetRender

private class Opt(val id: Long?, val label: String)

internal fun pathOf(n: NoteEntity, by: Map<Long, NoteEntity>): String {
    val parts = ArrayList<String>()
    var c: NoteEntity? = n
    var guard = 0
    while (c != null && guard++ < 30) {
        parts.add(c.displayTitle().ifBlank { "Без названия" })
        c = c.parentId?.let { by[it] }
    }
    return parts.reversed().joinToString(" / ")
}

@Composable
private fun PickDialog(title: String, options: List<Opt>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, options) { options.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }.take(80) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(shown) { o ->
                        Text(o.label, Modifier.fillMaxWidth().clickable { onPick(o.id) }.padding(vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Chips(items: List<String>, selected: Int, onSel: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, t -> FilterChip(selected = selected == i, onClick = { onSel(i) }, label = { Text(t) }) }
    }
}

/** Настройка виджета рабочего стола: открывается после входа в приложение. */
@Composable
fun WidgetConfigDialog(vm: AppViewModel, wid: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isQuick = remember { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(wid)?.provider?.className?.endsWith("QuickWidget") == true }
    val old = remember { WidgetPrefs.get(ctx, wid) }
    val kinds = listOf("folder" to "Папка", "note" to "Заметка", "dates" to "Сроки")
    var kind by remember { mutableStateOf(if (isQuick) "quick" else old?.kind?.takeIf { it != "quick" } ?: "folder") }
    var title by remember { mutableStateOf(old?.title ?: "") }
    var folderId by remember { mutableStateOf(old?.folderId) }
    var noteId by remember { mutableStateOf(old?.noteId) }
    var count by remember { mutableStateOf(old?.count ?: 4) }
    var theme by remember { mutableStateOf(old?.theme ?: 0) }
    var size by remember { mutableStateOf(old?.size ?: 1) }
    var add by remember { mutableStateOf(old?.add ?: true) }
    var newest by remember { mutableStateOf(old?.newestFirst ?: true) }
    var template by remember { mutableStateOf(old?.template ?: "") }
    var daily by remember { mutableStateOf(old?.daily ?: false) }
    var snip by remember { mutableStateOf(old?.snippetId) }
    val qSnip = remember { mutableStateListOf<Long?>(*Array(3) { old?.quick?.getOrNull(it)?.snippetId }) }
    val snippets by vm.allSnippets.collectAsState()
    val qDaily = remember { mutableStateListOf(*Array(3) { old?.quick?.getOrNull(it)?.daily ?: false }) }
    val qLabel = remember { mutableStateListOf(*Array(3) { old?.quick?.getOrNull(it)?.label ?: "" }) }
    val qFolder = remember { mutableStateListOf<Long?>(*Array(3) { old?.quick?.getOrNull(it)?.folderId }) }
    val qTpl = remember { mutableStateListOf(*Array(3) { old?.quick?.getOrNull(it)?.template ?: "" }) }
    var notes by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    var pick by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { notes = vm.allNotesOnce() }
    val by = remember(notes) { notes.associateBy { it.id } }
    fun snipLabel(id: Long?) = id?.let { i -> snippets.firstOrNull { it.id == i }?.label } ?: "без шаблона"
    fun folderLabel(id: Long?) = id?.let { by[it]?.let { n -> pathOf(n, by) } } ?: "Весь список (корень)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isQuick) "Виджет: быстрый ввод" else "Виджет: список") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isQuick) {
                    Text("Что показывать", style = MaterialTheme.typography.labelLarge)
                    Chips(kinds.map { it.second }, kinds.indexOfFirst { it.first == kind }.coerceAtLeast(0)) { kind = kinds[it].first }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Заголовок (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    when (kind) {
                        "folder" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Папка дня (по сегодняшней дате)", Modifier.weight(1f))
                                Switch(checked = daily, onCheckedChange = { daily = it })
                            }
                            if (daily) Text("Выберите родительскую папку, например «Работа». Виджет сам найдёт внутри неё папку с сегодняшней датой в названии (Работа / 2026 / Ноябрь / 22.11.26); если её нет — создаст по образцу последней.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { pick = "folder" }, modifier = Modifier.fillMaxWidth()) { Text((if (daily) "Родительская папка: " else "Папка: ") + folderLabel(folderId)) }
                            Text("Сколько строк", style = MaterialTheme.typography.labelLarge)
                            Chips((2..6).map { it.toString() }, count - 2) { count = it + 2 }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Сначала недавно изменённые", Modifier.weight(1f))
                                Switch(checked = newest, onCheckedChange = { newest = it })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Кнопка «+» новая заметка", Modifier.weight(1f))
                                Switch(checked = add, onCheckedChange = { add = it })
                            }
                            if (add) OutlinedButton(onClick = { pick = "tpl" }, modifier = Modifier.fillMaxWidth()) { Text("Шаблон: " + snipLabel(snip)) }
                        }
                        "note" -> OutlinedButton(onClick = { pick = "note" }, modifier = Modifier.fillMaxWidth()) {
                            Text("Заметка: " + (noteId?.let { by[it]?.let { n -> pathOf(n, by) } } ?: "не выбрана"))
                        }
                        else -> {
                            Text("Подписки, долги, даты и гарантии из модуля «Сроки и даты».", style = MaterialTheme.typography.bodySmall)
                            Text("Сколько строк", style = MaterialTheme.typography.labelLarge)
                            Chips((2..6).map { it.toString() }, count - 2) { count = it + 2 }
                        }
                    }
                } else {
                    Text("До трёх кнопок: нажатие сразу открывает новую заметку, названную как кнопка, в выбранной папке (или в папке сегодняшнего дня). Пустое название — кнопки нет.", style = MaterialTheme.typography.bodySmall)
                    for (i in 0..2) {
                        Text("Кнопка ${i + 1}", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(value = qLabel[i], onValueChange = { qLabel[i] = it }, label = { Text("Название на кнопке") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Папка дня (по сегодняшней дате)", Modifier.weight(1f))
                            Switch(checked = qDaily[i], onCheckedChange = { qDaily[i] = it })
                        }
                        OutlinedButton(onClick = { pick = "q$i" }, modifier = Modifier.fillMaxWidth()) { Text((if (qDaily[i]) "Родительская папка: " else "Папка: ") + folderLabel(qFolder[i])) }
                        OutlinedButton(onClick = { pick = "t$i" }, modifier = Modifier.fillMaxWidth()) { Text("Шаблон: " + snipLabel(qSnip[i])) }
                    }
                }
                Text("Оформление", style = MaterialTheme.typography.labelLarge)
                Chips(listOf("Как в системе", "Светлый", "Тёмный", "Прозрачный"), theme) { theme = it }
                if (!isQuick) Chips(listOf("Мелкий текст", "Средний", "Крупный"), size) { size = it }
                Text("Тексты на рабочем столе хранятся в зашифрованном виде; выключить их показ можно в Настройки → Виджеты.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cfg = WidgetCfg(
                    kind = kind, title = title.trim(), folderId = folderId, noteId = noteId, count = count, theme = theme, size = size, add = add,
                    newestFirst = newest, template = template,
                    quick = (0..2).map { QuickBtn(qLabel[it].trim(), qFolder[it], qTpl[it], qDaily[it], qSnip[it]) },
                    daily = daily, snippetId = snip,
                )
                WidgetPrefs.put(ctx, wid, cfg)
                WidgetRender.updateAll(ctx)
                vm.refreshWidgets()
                Toast.makeText(ctx, "Виджет настроен. Вернитесь на рабочий стол", Toast.LENGTH_LONG).show()
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    val tplOpts = listOf(Opt(null, "Без шаблона")) + snippets.map { Opt(it.id, it.label) }
    when (val p = pick) {
        "folder" -> TreePickDialog("Папка", notes, true, folderId, { folderId = it; pick = null }, { pick = null })
        "note" -> TreePickDialog("Заметка", notes, false, noteId?.let { by[it]?.parentId }, { noteId = it; pick = null }, { pick = null })
        "tpl" -> PickDialog("Шаблон новой заметки", tplOpts, { snip = it; pick = null }, { pick = null })
        null -> {}
        else -> {
            val i = p.substring(1).toInt()
            if (p.startsWith("t")) PickDialog("Шаблон для кнопки", tplOpts, { qSnip[i] = it; pick = null }, { pick = null })
            else TreePickDialog("Папка для кнопки", notes, true, qFolder[i], { qFolder[i] = it; pick = null }, { pick = null })
        }
    }
}

/**
 * Выбор папки (или заметки) по дереву: заходим вглубь нажатием на папку, «Выбрать эту папку» берёт открытую.
 * [start] — с какой папки начать (null — с корня). В режиме заметок нажатие на заметку выбирает её.
 */
@Composable
private fun TreePickDialog(title: String, notes: List<NoteEntity>, foldersOnly: Boolean, start: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val by = remember(notes) { notes.associateBy { it.id } }
    var cur by remember { mutableStateOf(start?.takeIf { by[it]?.isFolder == true }) }
    val kids = remember(notes, cur, foldersOnly) {
        notes.filter { it.parentId == cur && (it.isFolder || !foldersOnly) }
            .sortedWith(compareBy<NoteEntity>({ !it.isFolder }, { it.sortIndex }, { it.displayTitle().lowercase() }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(cur?.let { by[it]?.let { n -> pathOf(n, by) } } ?: "Главный экран", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (cur != null) item {
                        Text("⬆  Наверх", Modifier.fillMaxWidth().clickable { cur = by[cur]?.parentId }.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                    }
                    if (kids.isEmpty()) item { Text(if (foldersOnly) "Вложенных папок нет" else "Пусто", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall) }
                    items(kids) { n ->
                        val name = n.displayTitle().ifBlank { "Без названия" }
                        Text(
                            (if (n.isFolder) "📁  " else "📝  ") + name + (if (n.isFolder) "   ›" else ""),
                            Modifier.fillMaxWidth().clickable { if (n.isFolder) cur = n.id else onPick(n.id) }.padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { if (foldersOnly) TextButton(onClick = { onPick(cur) }) { Text("Выбрать эту папку") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Страница «Виджеты» в настройках. */
@Composable
fun WidgetSettingsSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(WidgetPrefs.contentOn(ctx)) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Как добавить: долгое нажатие на рабочем столе → «Виджеты» → «МоиЗаметки». Есть два виджета: «список» (папка, одна заметка или ближайшие сроки) и «быстрый ввод» (кнопки новой заметки). Настройка открывается в приложении; изменить её позже — долгое нажатие на виджет → «Настроить», либо нажать на заголовок виджета.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Показывать тексты заметок на рабочем столе", Modifier.weight(1f))
            Switch(checked = on, onCheckedChange = {
                on = it
                WidgetPrefs.setContent(ctx, it)
                if (it) vm.refreshWidgets() else {
                    ru.stasoft.mynotes.widgets.WidgetStore.clear(ctx)
                    WidgetRender.updateAll(ctx)
                }
            })
        }
        Text(
            "Рабочий стол виден без пароля. Тексты для виджетов хранятся отдельно, зашифрованными ключом телефона, но любой, кто держит разблокированный телефон, их увидит. Если выключить показ, виджеты останутся только кнопками быстрого ввода.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { vm.refreshWidgets(); Toast.makeText(ctx, "Виджеты обновляются", Toast.LENGTH_SHORT).show() }) { Text("Обновить виджеты сейчас") }
        Text("Виджеты обновляются, когда вы выходите из приложения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Включение и выключение пароля (Настройки → Безопасность). */
@Composable
fun PasswordToggleDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val turningOff = !vm.noPassword
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hide = androidx.compose.ui.text.input.PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (turningOff) "Выключить пароль?" else "Включить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (turningOff) (if (vm.biometricEnabled) "Подтвердите отпечатком (если пароль забыт) или введите текущий пароль. " else "") + "Приложение будет открываться сразу, без пароля и автоблокировки. База останется зашифрованной, но ключ будет храниться в защищённой области телефона, а не вычисляться из пароля: любой, у кого в руках разблокированный телефон, увидит записи. Резервные копии в этом режиме тоже не защищены паролем."
                    else "Придумайте пароль: им снова будет защищён вход. Если забыть его, восстановить данные нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text(if (turningOff) "Текущий пароль" else "Новый пароль") }, singleLine = true, visualTransformation = hide, modifier = Modifier.fillMaxWidth())
                if (!turningOff) OutlinedTextField(value = pw2, onValueChange = { pw2 = it }, label = { Text("Повторите пароль") }, singleLine = true, visualTransformation = hide, modifier = Modifier.fillMaxWidth())
                if (turningOff && vm.biometricEnabled) {
                    val ctx = LocalContext.current
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val activity = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstOrNull { it is androidx.fragment.app.FragmentActivity } as? androidx.fragment.app.FragmentActivity
                        val cipher = ru.stasoft.mynotes.crypto.BiometricVault.decryptCipher(ctx)
                        if (activity == null) {
                            err = "Не удалось показать запрос отпечатка: введите пароль"
                        } else if (cipher == null) {
                            err = "Ключ отпечатка недоступен: введите пароль"
                        } else {
                            showBiometricPrompt(
                                activity = activity, title = "Выключить пароль", negativeText = "Отмена", cipher = cipher,
                                onSuccess = { c ->
                                    busy = true
                                    scope.launch {
                                        val r = vm.disablePasswordWithBiometric(c)
                                        busy = false
                                        if (r == null) onDismiss() else err = r
                                    }
                                },
                                onFailure = { err = it },
                            )
                        }
                    }) { Text("Подтвердить отпечатком") }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && pw.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val r = if (turningOff) vm.disablePassword(pw) else vm.enablePassword(pw, pw2)
                    busy = false
                    if (r == null) onDismiss() else err = r
                }
            }) { Text(if (turningOff) "Выключить" else "Включить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}
