package ru.stasoft.mynotes.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.stasoft.mynotes.backup.BackupManager
import ru.stasoft.mynotes.backup.BackupScheduler
import java.text.DateFormat
import java.util.Date

/** Управление резервными копиями: расписание, место хранения (в том числе папка Google Диска), список копий. */
@Composable
fun BackupDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(BackupScheduler.enabled(ctx)) }
    var hours by remember { mutableIntStateOf(BackupScheduler.intervalHours(ctx)) }
    var hour by remember { mutableIntStateOf(BackupScheduler.hour(ctx)) }
    var keep by remember { mutableIntStateOf(BackupManager.keepCount(ctx)) }
    var tree by remember { mutableStateOf(BackupManager.treeUri(ctx)) }
    var treeName by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<BackupManager.Item>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var hourMenu by remember { mutableStateOf(false) }
    var restoreItem by remember { mutableStateOf<BackupManager.Item?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var saveAsItem by remember { mutableStateOf<BackupManager.Item?>(null) }

    LaunchedEffect(tick, tree) {
        items = withContext(Dispatchers.IO) { BackupManager.list(ctx) }
        treeName = withContext(Dispatchers.IO) { tree?.let { folderName(ctx, it) } ?: "" }
    }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            BackupManager.setTree(ctx, uri)
            tree = uri
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = saveAsItem
        saveAsItem = null
        if (uri != null && item != null) {
            scope.launch {
                busy = true
                status = withContext(Dispatchers.IO) {
                    try {
                        BackupManager.openStream(ctx, item)!!.use { input ->
                            ctx.contentResolver.openOutputStream(uri, "w")!!.use { input.copyTo(it) }
                        }
                        "Копия сохранена"
                    } catch (e: Exception) {
                        "Не удалось сохранить: ${e.message}"
                    }
                }
                busy = false
            }
        }
    }

    fun savePrefs() {
        BackupManager.prefs(ctx).edit().putBoolean("auto", auto).putInt("hours", hours).putInt("hour", hour).putInt("keep", keep).apply()
        BackupScheduler.apply(ctx)
    }

    fun doRestore(open: () -> java.io.InputStream?) {
        scope.launch {
            busy = true
            status = null
            vm.lock()
            val err = withContext(Dispatchers.IO) {
                try {
                    val s = open() ?: throw IllegalStateException("файл недоступен")
                    s.use { BackupManager.restore(ctx, it) }
                    null
                } catch (e: Exception) {
                    e.message ?: "ошибка"
                }
            }
            busy = false
            if (err != null) {
                status = "Не удалось восстановить: $err"
            } else {
                // Перезапуск: связка ключей и база заменены, приложение откроется заново с паролем из копии.
                val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (launch != null) ctx.startActivity(launch)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text("Резервные копии", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupTitle("По расписанию")
                    Row(
                        Modifier.fillMaxWidth().clickable { auto = !auto; savePrefs() }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Делать копии автоматически", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Работает в фоне, пароль вводить не нужно: копируются уже зашифрованные данные.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = auto, onCheckedChange = { auto = it; savePrefs() })
                    }
                    if (auto) {
                        listOf(24 to "Каждый день", 72 to "Раз в 3 дня", 168 to "Раз в неделю").forEach { (h, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { hours = h; savePrefs() }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = hours == h, onClick = { hours = h; savePrefs() })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Время (примерно)", modifier = Modifier.weight(1f))
                            androidx.compose.foundation.layout.Box {
                                TextButton(onClick = { hourMenu = true }) { Text("%02d:00".format(hour)) }
                                DropdownMenu(expanded = hourMenu, onDismissRequest = { hourMenu = false }) {
                                    (0..23).forEach { h ->
                                        DropdownMenuItem(text = { Text("%02d:00".format(h)) }, onClick = { hourMenu = false; hour = h; savePrefs() })
                                    }
                                }
                            }
                        }
                    }
                    Text("Хранить копий", modifier = Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(5, 10, 30).forEach { n ->
                            RadioButton(selected = keep == n, onClick = { keep = n; savePrefs() })
                            Text("$n", modifier = Modifier.clickable { keep = n; savePrefs() }.padding(end = 12.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    BackupTitle("Куда сохранять")
                    Text(
                        "Копии всегда лежат в памяти приложения (пропадают при удалении приложения). Чтобы они " +
                            "пережили переустановку и телефон, выберите папку: например «Google Диск» в окне выбора папки " +
                            "(нужно приложение «Диск» с вашим аккаунтом). Само приложение в интернет не выходит.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (tree == null) "Папка не выбрана" else "Папка: " + treeName.ifEmpty { "выбрана" },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppButton("Выбрать папку", { pickTree.launch(null) }, Modifier.weight(1f), icon = Icons.Filled.FolderOpen, tone = 1)
                        if (tree != null) {
                            TextButton(onClick = {
                                try { ctx.contentResolver.releasePersistableUriPermission(tree!!, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
                                BackupManager.setTree(ctx, null)
                                tree = null
                            }) { Text("Отключить") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    BackupTitle("Копии")
                    val last = BackupManager.prefs(ctx).getLong("last_time", 0L)
                    if (last > 0) {
                        val ok = BackupManager.prefs(ctx).getBoolean("last_ok", true)
                        Text(
                            "Последняя: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last)) +
                                (if (ok) " — успешно" else " — с ошибкой") +
                                (BackupManager.prefs(ctx).getString("last_msg", null)?.let { "\n$it" } ?: ""),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    status?.let {
                        Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        AppButton(
                            "Создать копию сейчас",
                            {
                                scope.launch {
                                    busy = true
                                    status = withContext(Dispatchers.IO) {
                                        try { BackupManager.backupNow(ctx, auto = false) } catch (e: Exception) { e.message ?: "ошибка" }
                                    }
                                    busy = false
                                    tick++
                                }
                            },
                            Modifier.weight(1f),
                            icon = Icons.Filled.Backup,
                            tone = 0,
                            enabled = !busy,
                        )
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AppButton("Восстановить из файла…", { pickFile.launch(arrayOf("*/*")) }, Modifier.weight(1f), icon = Icons.Filled.Restore, tone = 2, enabled = !busy)
                    }
                    if (items.isEmpty()) {
                        Text("Копий пока нет.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items.forEach { it0 ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it0.time)),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    (if (it0.local) "в приложении" else "в папке") + " · " + sizeText(it0.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { restoreItem = it0 }, enabled = !busy) { Icon(Icons.Filled.Restore, "Восстановить") }
                            IconButton(onClick = { saveAsItem = it0; saveAs.launch(it0.name) }, enabled = !busy) { Icon(Icons.Filled.SaveAlt, "Сохранить как") }
                            IconButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { BackupManager.delete(ctx, it0) }
                                    tick++
                                }
                            }, enabled = !busy) { Icon(Icons.Filled.Delete, "Удалить") }
                        }
                        HorizontalDivider()
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
                }
            }
        }
    }

    val confirm = restoreItem
    val confirmUri = restoreUri
    if (confirm != null || confirmUri != null) {
        AlertDialog(
            onDismissRequest = { restoreItem = null; restoreUri = null },
            title = { Text("Восстановить из копии?") },
            text = {
                Text(
                    "Текущие записи будут заменены данными из копии (перед этим автоматически создаётся копия текущего " +
                        "состояния). Приложение перезапустится, войти нужно будет паролем, который был на момент копии. " +
                        "Вход по отпечатку придётся включить заново.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreItem = null
                    restoreUri = null
                    if (confirm != null) doRestore { BackupManager.openStream(ctx, confirm) }
                    else doRestore { ctx.contentResolver.openInputStream(confirmUri!!) }
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { restoreItem = null; restoreUri = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun BackupTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun sizeText(bytes: Long): String =
    if (bytes >= 1_048_576) "%.1f МБ".format(bytes / 1_048_576.0) else "%d КБ".format((bytes + 1023) / 1024)

private fun folderName(ctx: Context, tree: Uri): String = try {
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else ""
    } ?: ""
} catch (_: Exception) {
    ""
}
