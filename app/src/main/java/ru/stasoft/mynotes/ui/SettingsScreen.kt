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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val MODS = ru.stasoft.mynotes.modules.Modules

private val MODULE_PAGES = listOf("budget", "health", "auto", "day", "giga", "route")

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
    var page by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // Назад: из вложенной страницы — на страницу раздела, из раздела — к списку, из списка — закрыть.
    fun goBack() {
        page = when (page) {
            null -> { onDismiss(); null }
            "icon", "widgets" -> "look"
            "budget", "health", "auto", "day", "giga", "route" -> "mods"
            else -> null
        }
    }
    Dialog(onDismissRequest = { goBack() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                val pageTitles = mapOf(
                    "look" to "Внешний вид", "icon" to "Значок приложения", "security" to "Безопасность", "data" to "Данные и копии", "lists" to "Подсказки значений",
                    "modules" to "Модули", "mods" to "Настройки модулей", "widgets" to "Виджеты", "budget" to "Бюджет", "health" to "Показатели", "auto" to "Авто", "day" to "Папка дня", "giga" to "Голосовой ввод", "route" to "Маршрут", "about" to "О программе",
                )
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Text(pageTitles[page] ?: "Настройки", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (page) {
                        null -> {
                            SettingsAction(Icons.Filled.Palette, "Внешний вид", "Тема, кнопки, значок, виджеты") { page = "look" }
                            SettingsAction(Icons.Filled.Fingerprint, "Безопасность", "Пароль и вход по отпечатку") { page = "security" }
                            SettingsAction(Icons.Filled.Backup, "Данные и копии", "Резервные копии, импорт, проверка показаний") { page = "data" }
                            SettingsAction(Icons.Filled.Lightbulb, "Подсказки значений", "Списки для полей: «Магазин:», «Авто:»…") { page = "lists" }
                            SettingsAction(Icons.Filled.Extension, "Модули", "Включение необязательных функций") { page = "modules" }
                            if (MODULE_PAGES.any { MODS.on(it) }) SettingsAction(Icons.Filled.Tune, "Настройки модулей", "Бюджет, авто, папка дня и другие") { page = "mods" }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            SettingsAction(Icons.Filled.Info, "О программе", "МоиЗаметки" + if (version.isNotEmpty()) " · версия $version" else "") { page = "about" }
                        }
                        "mods" -> {
                            if (MODS.on("budget")) SettingsAction(Icons.Filled.Savings, "Бюджет", "Категории и лимиты расходов") { page = "budget" }
                            if (MODS.on("health")) SettingsAction(Icons.Filled.Info, "Показатели", "Список показателей здоровья") { page = "health" }
                            if (MODS.on("auto")) SettingsAction(Icons.Filled.DirectionsCar, "Авто", "Машины, регламент ТО, привязка заметок") { page = "auto" }
                            if (MODS.on("day")) SettingsAction(Icons.Filled.CalendarMonth, "Папка дня", "График и шаблоны для нового дня") { page = "day" }
                            if (MODS.on("giga")) SettingsAction(Icons.Filled.Mic, "Голосовой ввод", "Ключ GigaChat для ИИ-разбора") { page = "giga" }
                            if (MODS.on("route")) SettingsAction(Icons.Filled.Directions, "Маршрут", "Город для адресов без города") { page = "route" }
                            if (MODULE_PAGES.none { MODS.on(it) }) Text("Включите нужные модули в разделе «Модули»: у тех, что имеют настройки, они появятся здесь.", Modifier.padding(16.dp))
                        }
                        "look" -> {
                    SettingsAction(Icons.Filled.Apps, "Значок приложения", "Шесть вариантов значка") { page = "icon" }
                    SettingsAction(Icons.Filled.Widgets, "Виджеты", "Рабочий стол: список, заметка, быстрый ввод") { page = "widgets" }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingsTitle("Тема")
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
                    BUTTON_SKINS.forEachIndexed { style, label ->
                        Column(Modifier.fillMaxWidth().clickable { vm.chooseButtonStyle(style) }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppRadio(selected = vm.buttonStyle == style, onClick = { vm.chooseButtonStyle(style) })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                            SkinPreview(style, Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { showKit = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Посмотреть стеклянный набор…") }
                        }
                        "security" -> {
                    SettingsTitle("Безопасность")
                    var pwDialog by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clickable { pwDialog = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Защита паролем", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (vm.noPassword) "Выключена: приложение открывается сразу" else "Включена: пароль при входе и автоблокировка",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(checked = !vm.noPassword, onCheckedChange = { pwDialog = true })
                    }
                    if (pwDialog) PasswordToggleDialog(vm) { pwDialog = false }
                    if (vm.noPassword) {
                        Text("Вход по отпечатку доступен, когда включён пароль.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    } else if (vm.biometricAvailable()) {
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
                        }
                        "data" -> {
                    SettingsTitle("Данные")
                    SettingsAction(Icons.Filled.Backup, "Резервные копии…", "Расписание, папка (Google Диск), восстановление", onBackup)
                    SettingsAction(Icons.Filled.FileUpload, "Импорт из .mtnt", "Загрузить базу MyTreeNotes в корень", onImport)
                    SettingsAction(Icons.Filled.FactCheck, "Проверка показаний…", "Найти ошибки в пробеге и других показаниях", onCheck)
                        }
                        "icon" -> IconSettingsSection()
                        "lists" -> FieldListsSection(vm)
                        "modules" -> ModulesSection(vm)
                        "budget" -> BudgetSettingsSection(vm)
                        "health" -> HealthSettingsSection(vm)
                        "auto" -> AutoSettingsSection(vm)
                        "widgets" -> WidgetSettingsSection(vm)
                        "day" -> AutoDaySettingsSection(vm)
                        "giga" -> VoiceSettingsSection(vm)
                        "route" -> RouteSettingsSection()
                        "about" -> {
                            Text(
                                "МоиЗаметки" + if (version.isNotEmpty()) " · версия $version" else "",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            Text(
                                "Записная книжка с шифрованием, шаблонами, переменными и напоминаниями. Все данные хранятся только на вашем телефоне.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            val aboutCtx = LocalContext.current
                            SettingsAction(Icons.Filled.Info, "Связь с автором", "Telegram: @Stasoft") {
                                runCatching {
                                    aboutCtx.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/Stasoft"))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        }
                    }
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
