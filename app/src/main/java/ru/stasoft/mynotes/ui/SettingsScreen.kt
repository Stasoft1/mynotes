package ru.stasoft.mynotes.ui

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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Технические пункты: внешний вид, безопасность, данные, версия. */
@Composable
fun SettingsDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onCheck: () -> Unit,
    onBackup: () -> Unit,
    onBioOn: () -> Unit,
) {
    val ctx = LocalContext.current
    var showKit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showKit) GlassKitDialog(onDismiss = { showKit = false })
    val version = remember(ctx) {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Text("Настройки", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsTitle("Внешний вид")
                    listOf(0 to "Как в системе", 1 to "Светлая", 2 to "Тёмная").forEach { (mode, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setTheme(mode) }.padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppRadio(selected = vm.themeMode == mode, onClick = { vm.setTheme(mode) })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    SettingsTitle("Кнопки")
                    listOf(0 to "Объёмные", 1 to "Стеклянные (пастельные)").forEach { (style, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.chooseButtonStyle(style) }.padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppRadio(selected = vm.buttonStyle == style, onClick = { vm.chooseButtonStyle(style) })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { showKit = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Посмотреть стеклянный набор…") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingsTitle("Безопасность")
                    if (vm.biometricAvailable()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (vm.biometricEnabled) vm.disableBiometric() else onBioOn() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text("Вход по отпечатку", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Открывать базу отпечатком вместо пароля",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AppSwitch(
                                checked = vm.biometricEnabled,
                                onCheckedChange = { if (it) onBioOn() else vm.disableBiometric() },
                            )
                        }
                    } else {
                        Text(
                            "Отпечаток на этом устройстве недоступен.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingsTitle("Данные")
                    SettingsAction(Icons.Filled.Backup, "Резервные копии…", "Расписание, папка (Google Диск), восстановление", onBackup)
                    SettingsAction(Icons.Filled.FileUpload, "Импорт из .mtnt", "Загрузить базу MyTreeNotes в корень", onImport)
                    SettingsAction(Icons.Filled.FactCheck, "Проверка показаний…", "Найти ошибки в пробеге и других показаниях", onCheck)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingsTitle("О программе")
                    Text(
                        "МоиЗаметки" + if (version.isNotEmpty()) " · версия $version" else "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
