package ru.stasoft.mynotes.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlinx.coroutines.Job
import ru.stasoft.mynotes.reminders.ReminderLogic
import ru.stasoft.mynotes.reminders.ReminderSync
import ru.stasoft.mynotes.reminders.GeoSync
import ru.stasoft.mynotes.crypto.BiometricVault
import ru.stasoft.mynotes.crypto.Vault
import ru.stasoft.mynotes.data.AppDatabase
import ru.stasoft.mynotes.data.DEFAULT_SNIPPETS
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.PlaceEntity
import ru.stasoft.mynotes.data.SnippetEntity
import ru.stasoft.mynotes.data.FolderTitle
import ru.stasoft.mynotes.data.ReadingIssue
import ru.stasoft.mynotes.data.expandWithVars
import ru.stasoft.mynotes.data.expandSnippet
import ru.stasoft.mynotes.data.searchNotes
import ru.stasoft.mynotes.modules.Modules
import ru.stasoft.mynotes.data.resolveTitle
import ru.stasoft.mynotes.data.formatValues
import ru.stasoft.mynotes.data.displayTitle
import ru.stasoft.mynotes.data.parseSpans
import ru.stasoft.mynotes.data.spansToJson
import ru.stasoft.mynotes.data.dateInTitle
import ru.stasoft.mynotes.data.VarDef
import ru.stasoft.mynotes.data.computeNumbers
import ru.stasoft.mynotes.data.parseVars
import ru.stasoft.mynotes.data.sortNotes
import ru.stasoft.mynotes.data.matchVoiceTemplate
import ru.stasoft.mynotes.data.templateFieldKeys
import ru.stasoft.mynotes.data.fillTemplateFromSpeech
import ru.stasoft.mynotes.data.fillTemplateFromMap
import ru.stasoft.mynotes.data.plainVoiceNote
import ru.stasoft.mynotes.data.stripVoiceLead
import ru.stasoft.mynotes.data.splitListItems
import ru.stasoft.mynotes.data.extractVoiceCommands
import ru.stasoft.mynotes.data.resolveIconName
import ru.stasoft.mynotes.importer.MtntImporter

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    init { Modules.init(app) }


    var unlocked by mutableStateOf(false)
        private set
    var needSetup by mutableStateOf(!Vault.isSetup(app))
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    /** Открытая в редакторе запись (null — редактор закрыт). */
    /** Тема: 0 — как в системе, 1 — светлая, 2 — тёмная. */
    var themeMode by mutableStateOf(app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getInt("theme", 0))
        private set

    /** Стиль кнопок: 0 — объёмные, 1 — стеклянные. */
    var buttonStyle by mutableStateOf(app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getInt("buttons", 0))
        private set

    fun chooseButtonStyle(style: Int) {
        buttonStyle = style
        app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putInt("buttons", style).apply()
    }

    fun setTheme(mode: Int) {
        themeMode = mode
        app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putInt("theme", mode).apply()
    }

    var editing by mutableStateOf<NoteEntity?>(null)
        private set

    /** Несохранённые правки открытой записи (до нажатия «Сохранить»). */
    data class Draft(val id: Long, val title: String, val body: String, val spans: String)
    var draft: Draft? = null

    /** Цепочка папок от корня до текущей. */
    var path by mutableStateOf<List<NoteEntity>>(emptyList())
        private set

    private val dbFlow = MutableStateFlow<AppDatabase?>(null)
    val folderId = MutableStateFlow<Long?>(null)
    val query = MutableStateFlow("")

    /** Режим сортировки текущей папки (у корня — ручной). */
    // Сортировка корня хранится в настройках (у корня нет своей записи); у остальных папок — в самой папке.
    private fun uiPrefs() = app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
    private fun rootSort() = uiPrefs().getInt("root_sort", 0)
    private fun rootFoldersFirst() = uiPrefs().getBoolean("root_folders_first", true)

    val sortMode = MutableStateFlow(rootSort())
    val foldersFirst = MutableStateFlow(rootFoldersFirst())

    /** Фильтры поиска: тип (0 всё, 1 заметки, 2 папки) и давность правки (0 всё, 1 неделя, 2 месяц, 3 год). */
    val searchType = MutableStateFlow(0)
    val searchAge = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<NoteEntity>> =
        combine(dbFlow, folderId, query, sortMode, foldersFirst, searchType, searchAge) { a -> a.toList() }
            .flatMapLatest { args ->
                val db = args[0] as AppDatabase?
                val fid = args[1] as Long?
                val q = args[2] as String
                val mode = args[3] as Int
                val ff = args[4] as Boolean
                val type = args[5] as Int
                val age = args[6] as Int
                when {
                    db == null -> flowOf(emptyList())
                    q.isBlank() -> db.notes().children(fid).map { sortNotes(it, mode, ff) }
                    else -> db.notes().allNotes().map { all -> searchNotes(all, q, type, age) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Значения переменных по записям (см. computeNumbers). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val numbers: StateFlow<Map<Long, List<Pair<String, Double>>>> =
        dbFlow
            .flatMapLatest { db ->
                if (db == null) flowOf(emptyMap())
                else db.notes().changeStamp().map {
                    try {
                        val rows = db.notes().numRows()
                        withContext(Dispatchers.Default) { computeNumbers(rows) }
                    } catch (_: Exception) {
                        emptyMap()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Папка сегодняшнего дня (по дате в названии) и значения её переменных, плюс объемлющая папка (месяц). */
    data class TodayInfo(
        val folder: FolderTitle,
        val values: List<Pair<String, Double>>,
        val parentTitle: String?,
        val parentValues: List<Pair<String, Double>>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<TodayInfo?> =
        dbFlow
            .flatMapLatest { db ->
                if (db == null) flowOf(null)
                else combine(db.notes().folderTitles(), numbers) { folders, nums ->
                    val d = java.time.LocalDate.now()
                    val hit = folders.firstOrNull { dateInTitle(it.title) == d } ?: return@combine null
                    val parent = folders.firstOrNull { it.id == hit.parentId }
                    TodayInfo(hit, nums[hit.id].orEmpty(), parent?.title, parent?.let { nums[it.id] }.orEmpty())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var backgroundedAt = 0L

    // ---------- быстрые вставки ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    val savedPlaces: StateFlow<List<PlaceEntity>> =
        dbFlow
            .flatMapLatest { db -> db?.notes()?.places() ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePlace(name: String, lat: Double, lon: Double, radius: Int) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.insertPlace(PlaceEntity(name = name.trim().ifBlank { "Место" }, lat = lat, lon = lon, radius = radius)) }
    }

    fun deletePlace(id: Long) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.deletePlace(id) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allSnippets: StateFlow<List<SnippetEntity>> =
        dbFlow
            .flatMapLatest { db -> db?.notes()?.allSnippets() ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Папки-предки открытой заметки, от ближайшей к корню: вставки этих папок доступны в редакторе. */
    var editingChain by mutableStateOf<List<Long>>(emptyList())
        private set

    private fun loadChain(note: NoteEntity) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val chain = mutableListOf<Long>()
            var cur = note.parentId
            while (cur != null && chain.size < 64) {
                chain.add(cur)
                cur = dao.get(cur)?.parentId
            }
            editingChain = chain
        }
    }

    private suspend fun seedSnippetsOnce(db: AppDatabase) {
        val prefs = app.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
        val dao = db.notes()
        if (!prefs.getBoolean("snippets_seeded", false)) {
            if (dao.snippetCount() == 0) {
                DEFAULT_SNIPPETS.forEachIndexed { i, (label, text) ->
                    dao.insertSnippet(SnippetEntity(folderId = null, label = label, text = text, sortIndex = i))
                }
            }
            prefs.edit().putBoolean("snippets_seeded", true).apply()
        }
        // Шаблон заявки с переменными папки: добавляется один раз, существующие шаблоны не трогаем.
        if (!prefs.getBoolean("cable_template_seeded", false)) {
            val sort = dao.maxSnippetSort() + 1
            dao.insertSnippet(SnippetEntity(folderId = null, label = "Кабель: взял на склад", text = "Взял вп: \nВзял кокс: ", sortIndex = sort))
            prefs.edit().putBoolean("cable_template_seeded", true).apply()
        }
        if (!prefs.getBoolean("request_template_seeded", false)) {
            val sort = dao.maxSnippetSort() + 1
            dao.insertSnippet(SnippetEntity(folderId = null, label = REQUEST_TEMPLATE_LABEL, text = REQUEST_TEMPLATE, sortIndex = sort))
            prefs.edit().putBoolean("request_template_seeded", true).apply()
        }
    }

    fun saveSnippet(id: Long?, folderId: Long?, label: String, text: String) {
        val dao = dao() ?: return
        viewModelScope.launch {
            if (id == null) {
                dao.insertSnippet(SnippetEntity(folderId = folderId, label = label, text = text, sortIndex = dao.maxSnippetSort() + 1))
            } else {
                val cur = allSnippets.value.firstOrNull { it.id == id } ?: return@launch
                dao.updateSnippet(cur.copy(label = label, text = text))
            }
        }
    }

    fun deleteSnippet(s: SnippetEntity) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.deleteSnippet(s) }
    }

    // ---------- напоминания ----------

    var showReminders by mutableStateOf(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val reminderItems: StateFlow<List<NoteEntity>> =
        dbFlow
            .flatMapLatest { db -> db?.notes()?.withReminders() ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var reminderJob: Job? = null

    private fun startReminderSync(db: AppDatabase) {
        reminderJob?.cancel()
        reminderJob = viewModelScope.launch(Dispatchers.IO) {
            // Сбой в уведомлениях не должен ронять приложение.
            try {
                launch {
                    try {
                        db.notes().withGeo().collect { GeoSync.rebuild(app, if (Modules.on("geo")) it else emptyList()) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("MyNotes", "geo sync failed", e)
                    }
                }
                ReminderSync.applyPending(app, db.notes())
                db.notes().withReminders().collect {
                    try {
                        ReminderSync.rebuild(app, it)
                    } catch (e: Exception) {
                        android.util.Log.e("MyNotes", "reminder rebuild failed", e)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MyNotes", "reminder sync failed", e)
            }
        }
    }

    fun setReminder(noteId: Long, dueAt: Long?, period: Int, leadDays: Int) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val cur = dao.get(noteId) ?: return@launch
            dao.update(
                cur.copy(
                    reminderAt = dueAt,
                    reminderPeriod = if (dueAt == null) 0 else period,
                    reminderCheckDays = if (dueAt == null) 0 else leadDays,
                    reminderDoneAt = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            if (editing?.id == noteId) editing = dao.get(noteId)
            if (props?.id == noteId) props = dao.get(noteId)
        }
    }

    /** Напоминание по месту: точка на карте (lat == null — убрать). */
    fun setGeo(noteId: Long, lat: Double?, lon: Double?, radius: Int, label: String, once: Boolean) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val cur = dao.get(noteId) ?: return@launch
            dao.update(
                cur.copy(
                    geoLat = lat, geoLon = if (lat == null) null else lon,
                    geoRadius = if (lat == null) 0 else radius, geoLabel = if (lat == null) "" else label,
                    geoOnce = once, updatedAt = System.currentTimeMillis(),
                ),
            )
            if (editing?.id == noteId) editing = dao.get(noteId)
            if (props?.id == noteId) props = dao.get(noteId)
        }
    }

    /** «Готово»: повторяющееся напоминание переходит на следующую дату, разовое считается выполненным. */
    fun acknowledge(note: NoteEntity) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val due = note.reminderAt ?: return@launch
            val now = System.currentTimeMillis()
            val next = ReminderLogic.advance(due, note.reminderPeriod, now)
            dao.update(note.copy(reminderAt = next ?: due, reminderDoneAt = now))
        }
    }

    private fun dao() = dbFlow.value?.notes()

    // ---------- вход ----------

    fun submitPassword(password: String, confirm: String?) {
        if (busy) return
        error = null
        if (needSetup) {
            if (password.length < Vault.minPasswordLength()) {
                error = "Пароль должен быть не короче ${Vault.minPasswordLength()} символов"
                return
            }
            if (password != confirm) {
                error = "Пароли не совпадают"
                return
            }
        } else {
            val wait = Vault.lockoutRemainingMs(app)
            if (wait > 0) {
                error = "Слишком много попыток. Подождите ${(wait + 999) / 1000} с"
                return
            }
        }
        busy = true
        viewModelScope.launch {
            val chars = password.toCharArray()
            val key: ByteArray? = withContext(Dispatchers.Default) {
                try {
                    if (needSetup) Vault.setup(app, chars) else Vault.unlock(app, chars)
                } finally {
                    chars.fill('\u0000')
                }
            }
            if (key == null) {
                error = "Неверный пароль"
                busy = false
                return@launch
            }
            openWith(key)
        }
    }

    /** Открывает базу мастер-ключом (после пароля или отпечатка). Ключ затирается. */
    private suspend fun openWith(key: ByteArray) {
        try {
            val db = withContext(Dispatchers.IO) {
                val d = AppDatabase.open(app, key)
                d.notes().count() // открываем базу сразу, чтобы ошибка проявилась здесь
                d
            }
            ru.stasoft.mynotes.data.PhotoStore.setKey(key)
            dbFlow.value = db
            withContext(Dispatchers.IO) {
                try {
                    db.notes().deleteOrphanPhotos()
                    ru.stasoft.mynotes.data.PhotoStore.sweep(app, db.notes().photoFiles().toSet())
                } catch (_: Exception) {
                }
            }
            needSetup = false
            unlocked = true
            error = null
            refreshPath()
            resumeEditingId?.let { id ->
                resumeEditingId = null
                db.notes().get(id)?.let { n ->
                    editing = n
                    loadChain(n)
                }
            }
            startReminderSync(db)
            try {
                withContext(Dispatchers.IO) { seedSnippetsOnce(db) }
            } catch (e: Exception) {
                android.util.Log.e("MyNotes", "snippet seed failed", e)
            }
        } catch (e: Exception) {
            error = "Не удалось открыть базу: ${e.message}"
        } finally {
            key.fill(0)
            busy = false
        }
    }

    // ---------- вход по отпечатку ----------

    var biometricEnabled by mutableStateOf(BiometricVault.isEnabled(app))
        private set

    fun biometricAvailable(): Boolean = BiometricVault.isAvailable(app)

    /** Вызывается после успешного отпечатка: расшифровывает мастер-ключ и открывает базу. */
    fun unlockWithBiometric(cipher: Cipher) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val key = try {
                BiometricVault.unwrap(app, cipher)
            } catch (e: Exception) {
                null
            }
            if (key == null) {
                error = "Не удалось войти по отпечатку. Введите пароль."
                busy = false
            } else {
                openWith(key)
            }
        }
    }

    fun onBiometricFailure(message: String) {
        error = message
    }

    /** Проверяет пароль и возвращает мастер-ключ (для включения отпечатка) или null. */
    suspend fun verifyPassword(password: String): ByteArray? {
        val wait = Vault.lockoutRemainingMs(app)
        if (wait > 0) {
            message = "Слишком много попыток. Подождите ${(wait + 999) / 1000} с"
            return null
        }
        val chars = password.toCharArray()
        return withContext(Dispatchers.Default) {
            try {
                Vault.unlock(app, chars)
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    fun storeBiometricKey(cipher: Cipher, master: ByteArray) {
        try {
            BiometricVault.store(app, cipher, master)
            biometricEnabled = true
            message = "Вход по отпечатку включён"
        } catch (e: Exception) {
            message = "Не удалось включить вход по отпечатку: ${e.message}"
        } finally {
            master.fill(0)
        }
    }

    fun disableBiometric() {
        BiometricVault.disable(app)
        biometricEnabled = false
        message = "Вход по отпечатку выключен"
    }

    /** Сохраняет ручной порядок записей текущей папки: ids — в новом порядке сверху вниз. */
    fun reorder(ids: List<Long>) {
        val db = dbFlow.value ?: return
        viewModelScope.launch {
            db.withTransaction { ids.forEachIndexed { i, id -> db.notes().setSort(id, i) } }
        }
    }

    /** Переносит запись в начало или в конец списка (ручной порядок). */
    fun moveToEdge(note: NoteEntity, top: Boolean) {
        val ids = items.value.map { it.id }.filter { it != note.id }
        reorder(if (top) listOf(note.id) + ids else ids + note.id)
    }

    /** Включает ручной порядок, сохранив тот порядок, в котором записи показаны сейчас. */
    fun makeManualKeepOrder() {
        reorder(items.value.map { it.id })
        setSortMode(0)
    }

    /** Режим сортировки текущей папки (см. NoteEntity.sortMode); для корня сохраняется в настройках. */
    fun setSortMode(mode: Int, folders: Boolean = foldersFirst.value) {
        val dao = dao() ?: return
        val id = folderId.value
        if (id == null) {
            uiPrefs().edit().putInt("root_sort", mode).putBoolean("root_folders_first", folders).apply()
            sortMode.value = mode
            foldersFirst.value = folders
            return
        }
        viewModelScope.launch {
            val f = dao.get(id) ?: return@launch
            dao.update(f.copy(sortMode = mode, foldersFirst = folders))
            sortMode.value = mode
            foldersFirst.value = folders
            path = path.map { if (it.id == id) it.copy(sortMode = mode, foldersFirst = folders) else it }
        }
    }

    /** Что была открыта на экране при блокировке: после входа возвращаем человека на то же место. */
    private var resumeEditingId: Long? = null

    fun lock() {
        val db = dbFlow.value
        reminderJob?.cancel()
        reminderJob = null
        resumeEditingId = props?.id ?: editing?.id ?: resumeEditingId
        dbFlow.value = null
        ru.stasoft.mynotes.data.PhotoStore.setKey(null)
        unlocked = false
        // Папка, выбор и поиск остаются, а тексты записей из памяти убираем: после входа они загрузятся заново.
        editing = null
        props = null
        path = emptyList()
        sortMode.value = rootSort()
        foldersFirst.value = rootFoldersFirst()
        error = null
        if (db != null) viewModelScope.launch(Dispatchers.IO) { db.close() }
    }

    fun onBackground() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onForeground() {
        if (unlocked && System.currentTimeMillis() - backgroundedAt > AUTO_LOCK_MS) lock()
    }

    // ---------- навигация ----------

    fun openFolder(note: NoteEntity) {
        selectedId = null
        query.value = ""
        folderId.value = note.id
        refreshPath()
    }

    /** Строка отчёта: запись дерева на глубине [depth] (1 — прямое содержимое папки) и название её родителя. */
    data class ReportRow(
        val id: Long,
        val title: String,
        val isFolder: Boolean,
        val depth: Int,
        val parentTitle: String,
        val date: java.time.LocalDate?,
    )

    /** Всё поддерево папки [id] (null — вся база) для отчёта, в порядке дерева; даты в названиях задают порядок. */
    suspend fun reportData(id: Long?): Pair<String?, List<ReportRow>> {
        val dao = dao() ?: return null to emptyList()
        val rows = dao.titleRows()
        return withContext(Dispatchers.Default) {
            val kids = rows.groupBy { it.parentId }
            val out = ArrayList<ReportRow>()
            fun walk(parent: Long?, parentTitle: String, depth: Int, inherited: java.time.LocalDate?) {
                val list = kids[parent].orEmpty()
                val sorted = if (list.isNotEmpty() && list.all { dateInTitle(it.title) != null }) list.sortedBy { dateInTitle(it.title) } else list
                for (r in sorted) {
                    val date = (if (r.isFolder) dateInTitle(r.title) else null) ?: inherited
                    out.add(ReportRow(r.id, r.title, r.isFolder, depth, parentTitle, date))
                    if (r.isFolder && depth < 12) walk(r.id, r.title, depth + 1, date)
                }
            }
            walk(id, "", 1, null)
            rows.firstOrNull { it.id == id }?.title to out
        }
    }

    /** Заправки в поддереве папки [id] (null — вся база): заметки с полем «Литры:». */
    suspend fun fuelStats(id: Long?, car: String? = null): ru.stasoft.mynotes.data.FuelSummary? {
        val dao = dao() ?: return null
        val rows = dao.titleRows()
        return withContext(Dispatchers.Default) {
            val kids = rows.groupBy { it.parentId }
            val fills = ArrayList<ru.stasoft.mynotes.data.Fill>()
            fun walk(parent: Long?, inherited: java.time.LocalDate?, depth: Int) {
                for (r in kids[parent].orEmpty()) {
                    val date = (if (r.isFolder) dateInTitle(r.title) else null) ?: inherited
                    if (r.isFolder) { if (depth < 12) walk(r.id, date, depth + 1) }
                    else ru.stasoft.mynotes.data.parseFill(r.head, date)?.let { fills.add(it) }
                }
            }
            walk(id, null, 1)
            // Заправки без «Авто:» относятся к машине по умолчанию.
            val dflt = if (Modules.on("auto")) defaultCar()?.name else null
            val mine = if (car == null) fills else fills.filter { (it.car ?: dflt) == car }
            ru.stasoft.mynotes.data.fuelSummary(mine)
        }
    }

    /** PDF заметки или всей папки [id] (null — текущая папка/вся база) и окно «Поделиться». */
    fun exportPdf(id: Long?) {
        val dao = dao() ?: return
        viewModelScope.launch {
            try {
                val nums = numbers.value
                val blocks = ArrayList<ru.stasoft.mynotes.ui.PdfBlock>()
                fun titleOf(n: NoteEntity) = resolveTitle(n.displayTitle(), nums[n.id]).ifBlank { "Без названия" }
                fun bodyOf(n: NoteEntity): String =
                    if (n.checklist) n.body.split("\n").joinToString("\n") { if (it.startsWith("[x] ") || it.startsWith("[X] ")) "☑ " + it.drop(4) else "☐ $it" }
                    else n.body
                suspend fun walk(parent: Long?, level: Int) {
                    for (n in dao.childrenOnce(parent)) {
                        if (n.isFolder) {
                            blocks.add(ru.stasoft.mynotes.ui.PdfBlock(titleOf(n), if (level == 1) 1 else 2))
                            nums[n.id]?.takeIf { it.isNotEmpty() }?.let { blocks.add(ru.stasoft.mynotes.ui.PdfBlock(formatValues(it), 0)) }
                            if (level < 12) walk(n.id, level + 1)
                        } else {
                            blocks.add(ru.stasoft.mynotes.ui.PdfBlock(titleOf(n), 2))
                            if (n.body.isNotBlank()) blocks.add(ru.stasoft.mynotes.ui.PdfBlock(bodyOf(n), 0))
                        }
                    }
                }
                val root = id?.let { dao.get(it) }
                val name: String
                if (root == null) {
                    name = "Заметки"
                    walk(folderId.value, 1)
                } else if (root.isFolder) {
                    name = titleOf(root)
                    blocks.add(ru.stasoft.mynotes.ui.PdfBlock(name, 1))
                    nums[root.id]?.takeIf { it.isNotEmpty() }?.let { blocks.add(ru.stasoft.mynotes.ui.PdfBlock(formatValues(it), 0)) }
                    walk(root.id, 2)
                } else {
                    name = titleOf(root)
                    blocks.add(ru.stasoft.mynotes.ui.PdfBlock(name, 1))
                    if (root.body.isNotBlank()) blocks.add(ru.stasoft.mynotes.ui.PdfBlock(bodyOf(root), 0))
                }
                if (blocks.isEmpty()) { message = "Нечего экспортировать"; return@launch }
                val f = withContext(Dispatchers.Default) { ru.stasoft.mynotes.ui.PdfExport.write(app, name, blocks) }
                ru.stasoft.mynotes.ui.PdfExport.share(app, f)
            } catch (e: Exception) {
                message = "Не удалось создать PDF: ${e.message}"
            }
        }
    }

    /** Переход в папку по id (из карточки «Сегодня»). */
    fun openFolderById(id: Long) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.get(id)?.let { openFolder(it) } }
    }

    fun goToPathIndex(index: Int) {
        // index = -1 — корень
        selectedId = path.getOrNull(index + 1)?.id
        scrollTick++
        folderId.value = if (index < 0) null else path[index].id
        refreshPath()
    }

    fun goUp(): Boolean {
        if (editing != null) {
            // Запасной путь: обычно «Назад» в редакторе обрабатывает сам редактор с вопросом о сохранении.
            draft?.let { d -> if (d.id == editing?.id) save(d.id, d.title, d.body, d.spans) }
            draft = null
            closeEditor()
            return true
        }
        if (showReminders) {
            showReminders = false
            return true
        }
        if (query.value.isNotBlank()) {
            query.value = ""
            return true
        }
        if (folderId.value == null) return false
        val left = folderId.value
        folderId.value = path.getOrNull(path.size - 2)?.id
        selectedId = left
        scrollTick++
        refreshPath()
        return true
    }

    private fun refreshPath() {
        val dao = dao() ?: return
        val target = folderId.value
        viewModelScope.launch {
            val chain = ArrayDeque<NoteEntity>()
            var cur = target
            while (cur != null) {
                val n = dao.get(cur) ?: break
                chain.addFirst(n)
                cur = n.parentId
            }
            path = chain.toList()
            sortMode.value = path.lastOrNull()?.sortMode ?: rootSort()
            foldersFirst.value = path.lastOrNull()?.foldersFirst ?: rootFoldersFirst()
        }
    }

    // ---------- записи ----------

    /** Выбранная в списке запись (одиночный тап): на неё действуют кнопки панели. */
    var selectedId by mutableStateOf<Long?>(null)
        private set

    fun select(note: NoteEntity) {
        selectedId = note.id
    }

    /** Растёт, когда список должен прокрутиться к выбранной записи (после выхода из папки или заметки). */
    var scrollTick by mutableStateOf(0)
        private set

    /** Запись, созданная в этой сессии редактора: если её оставили пустой, она удаляется при выходе. */
    private data class Created(val id: Long, val parentId: Long?, val parentWasFolder: Boolean)

    private var justCreated: Created? = null

    /** Нужно снова показать список шаблонов (после правки шаблона). */
    var reopenTemplates by mutableStateOf(false)

    /** Открывает шаблон в обычном редакторе: название шаблона-заметки и текст с оформлением. */
    fun editSnippet(s: SnippetEntity) {
        justCreated = null
        editing = NoteEntity(id = -s.id, title = s.title, body = s.text, spansJson = s.spansJson, createdAt = 0L, updatedAt = 0L)
        editingChain = emptyList()
    }

    fun newSnippet(label: String) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val id = dao.insertSnippet(
                SnippetEntity(folderId = folderId.value, label = label, text = "", sortIndex = dao.maxSnippetSort() + 1),
            )
            dao.allSnippetsOnce().firstOrNull { it.id == id }?.let { editSnippet(it) }
        }
    }

    fun renameSnippet(s: SnippetEntity, label: String) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.updateSnippet(s.copy(label = label)) }
    }

    /**
     * Подставляет в шаблон переменные папки ({переменные}, {Имя}, {сегодня}…) и переносит оформление на новый текст.
     * Возвращает готовый текст и оформление (JSON).
     */
    suspend fun expandTemplate(text: String, spansJson: String, parentId: Long?): Pair<String, String> {
        val dao = dao() ?: return text to spansJson
        val vars = if (parentId == null) emptyList() else inheritedVars(NoteEntity(parentId = parentId, createdAt = 0L, updatedAt = 0L)).map { it.first }
        val chain = mutableListOf<Long>()
        var cur = parentId
        while (cur != null && chain.size < 64) {
            chain.add(cur)
            cur = dao.get(cur)?.parentId
        }
        val values = numbers.value
        val lookup: (String) -> Double? = { name ->
            chain.firstNotNullOfOrNull { id -> values[id]?.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second }
        }
        val out = expandWithVars(text, vars, lookup)
        if (spansJson.isEmpty()) return out to ""
        val spans = parseSpans(spansJson, text.length)
        if (out == text) return out to spansJson
        fun map(pos: Int): Int = expandWithVars(text.substring(0, pos.coerceIn(0, text.length)), vars, lookup).length
        val mapped = spans.map { it.copy(start = map(it.start), end = map(it.end)) }.filter { it.end > it.start }
        return out to spansToJson(mapped)
    }

    /** Создаёт заметку из шаблона в текущей папке и открывает её. */
    fun newFromSnippet(s: SnippetEntity) {
        val dao = dao() ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val parent = folderId.value
            val (text0, spans) = expandTemplate(s.text, s.spansJson, parent)
            val text = withCar(text0)
            val id = dao.insert(
                NoteEntity(
                    parentId = parent, title = expandSnippet(s.title), body = text, spansJson = spans,
                    sortIndex = dao.maxSort(parent) + 1, createdAt = now, updatedAt = now,
                ),
            )
            justCreated = Created(id, parent, true)
            selectedId = id
            dao.get(id)?.let {
                editing = it
                loadChain(it)
            }
        }
    }

    // ---------- списки значений полей ----------

    var fieldLists by mutableStateOf(ru.stasoft.mynotes.data.FieldLists.parse(uiPrefs().getString("field_lists", "") ?: ""))
        private set

    fun saveFieldList(name: String, values: List<String>, oldName: String? = null) {
        val m = LinkedHashMap(fieldLists)
        if (oldName != null) m.remove(oldName)
        val n = name.trim().trimEnd(':').trim()
        val v = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (n.isNotEmpty() && v.isNotEmpty()) m[n] = v
        fieldLists = m
        uiPrefs().edit().putString("field_lists", ru.stasoft.mynotes.data.FieldLists.toJson(m)).apply()
    }

    fun deleteFieldList(name: String) {
        val m = LinkedHashMap(fieldLists)
        m.remove(name)
        fieldLists = m
        uiPrefs().edit().putString("field_lists", ru.stasoft.mynotes.data.FieldLists.toJson(m)).apply()
    }

    /** Значения для подсказки поля [name] (без учёта регистра): списки пользователя плюс те, что дают включённые модули. */
    fun fieldValues(name: String): List<String> {
        val own = fieldLists.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
        return (own + moduleFieldValues(name)).distinct()
    }

    /** Подсказки от модулей (например, «Авто» — названия машин). */
    private fun moduleFieldValues(name: String): List<String> =
        if (Modules.on("auto") && name.equals("Авто", ignoreCase = true)) cars.value.map { it.name } else emptyList()

    // ---------- модуль «Авто» ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    val cars: StateFlow<List<ru.stasoft.mynotes.data.CarEntity>> =
        dbFlow.flatMapLatest { db -> db?.notes()?.cars() ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val serviceItems: StateFlow<List<ru.stasoft.mynotes.data.ServiceItemEntity>> =
        dbFlow.flatMapLatest { db -> db?.notes()?.serviceItems() ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun defaultCar(): ru.stasoft.mynotes.data.CarEntity? = cars.value.firstOrNull { it.isDefault } ?: cars.value.firstOrNull()

    fun saveCar(c: ru.stasoft.mynotes.data.CarEntity) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val id = if (c.id == 0L) dao.insertCar(c.copy(sortIndex = cars.value.size, isDefault = c.isDefault || cars.value.isEmpty())) else { dao.updateCar(c); c.id }
            if (c.isDefault) dao.setDefaultCar(id)
        }
    }

    fun deleteCar(c: ru.stasoft.mynotes.data.CarEntity) {
        val dao = dao() ?: return
        viewModelScope.launch {
            dao.deleteCar(c.id)
            dao.deleteServiceItemsOf(c.id)
            if (c.isDefault) cars.value.firstOrNull { it.id != c.id }?.let { dao.setDefaultCar(it.id) }
        }
    }

    /** Строка «Авто:» для новой заметки про машину: по сказанному, иначе машина по умолчанию. Только если машин больше одной. */
    private fun withCar(body: String, spoken: String = ""): String {
        if (!Modules.on("auto") || cars.value.size < 2 || !ru.stasoft.mynotes.data.isAutoNote(body)) return body
        val car = ru.stasoft.mynotes.data.pickCar(spoken, cars.value) ?: defaultCar() ?: return body
        return ru.stasoft.mynotes.data.withCarLine(body, car.name)
    }

    /**
     * Старые заметки про машину без поля «Авто:» привязываются к [car]: строка добавляется в конец текста.
     * Даты [from]..[to] берутся из папок дня; пустые — без ограничения. Возвращает число заметок.
     */
    suspend fun assignCar(car: String, from: java.time.LocalDate?, to: java.time.LocalDate?): Int {
        val dao = dao() ?: return 0
        val rows = dao.titleRows()
        val kids = rows.groupBy { it.parentId }
        val ids = ArrayList<Long>()
        fun walk(parent: Long?, inherited: java.time.LocalDate?, depth: Int) {
            for (r in kids[parent].orEmpty()) {
                val date = (if (r.isFolder) dateInTitle(r.title) else null) ?: inherited
                if (r.isFolder) { if (depth < 12) walk(r.id, date, depth + 1); continue }
                if (!ru.stasoft.mynotes.data.isAutoNote(r.head) || ru.stasoft.mynotes.data.fieldText(r.head, "Авто") != null) continue
                if (from != null && (date == null || date.isBefore(from))) continue
                if (to != null && (date == null || date.isAfter(to))) continue
                ids.add(r.id)
            }
        }
        walk(null, null, 1)
        for (id in ids) {
            val n = dao.get(id) ?: continue
            dao.update(n.copy(body = ru.stasoft.mynotes.data.withCarLine(n.body, car)))
        }
        return ids.size
    }

    // ---------- фото ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    val photoCounts: StateFlow<Map<Long, Int>> =
        dbFlow.flatMapLatest { db -> if (db == null) flowOf(emptyMap()) else db.notes().photoCounts().map { l -> l.associate { it.noteId to it.n } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun photosOf(noteId: Long): Flow<List<ru.stasoft.mynotes.data.PhotoEntity>> =
        dbFlow.flatMapLatest { db -> if (db == null) flowOf(emptyList()) else db.notes().photosFor(noteId) }

    fun addPhotos(noteId: Long, uris: List<Uri>) {
        val dao = dao() ?: return
        viewModelScope.launch {
            var ok = 0
            for (u in uris) {
                try {
                    val name = withContext(Dispatchers.IO) { ru.stasoft.mynotes.data.PhotoStore.save(app, u) }
                    dao.insertPhoto(ru.stasoft.mynotes.data.PhotoEntity(noteId = noteId, file = name, createdAt = System.currentTimeMillis()))
                    ok++
                } catch (e: Exception) {
                    message = "Не удалось добавить фото: ${e.message}"
                }
            }
            if (ok > 0) message = if (ok == 1) "Фото добавлено" else "Добавлено фото: $ok"
        }
    }

    fun removePhoto(p: ru.stasoft.mynotes.data.PhotoEntity) {
        val dao = dao() ?: return
        viewModelScope.launch {
            dao.deletePhoto(p.id)
            withContext(Dispatchers.IO) { ru.stasoft.mynotes.data.PhotoStore.delete(app, p.file) }
        }
    }

    // ---------- автосоздание папки дня ----------

    var autoDay by mutableStateOf(uiPrefs().getBoolean("auto_day", false))
        private set
    var autoDaySnips by mutableStateOf(uiPrefs().getStringSet("auto_day_snips", emptySet()) ?: emptySet())
        private set
    private var autoDayTried: java.time.LocalDate? = null

    fun updateAutoDay(on: Boolean) {
        autoDay = on
        uiPrefs().edit().putBoolean("auto_day", on).apply()
        if (on) ensureToday()
    }

    fun toggleAutoDaySnippet(label: String) {
        autoDaySnips = if (label in autoDaySnips) autoDaySnips - label else autoDaySnips + label
        uiPrefs().edit().putStringSet("auto_day_snips", autoDaySnips).apply()
    }

    private fun formatLike(sample: String, d: java.time.LocalDate): String {
        val m = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})").find(sample)
            ?: return "%02d.%02d.%02d".format(d.dayOfMonth, d.monthValue, d.year % 100)
        fun p(v: Int, w: Int) = if (w >= 2) "%02d".format(v) else v.toString()
        val y = if (m.groupValues[3].length == 4) d.year.toString() else "%02d".format(d.year % 100)
        return p(d.dayOfMonth, m.groupValues[1].length) + "." + p(d.monthValue, m.groupValues[2].length) + "." + y
    }

    /**
     * Если папки сегодняшнего дня ещё нет, создаёт её рядом с последней папкой дня (в том же формате даты)
     * и кладёт в неё заметки из выбранных шаблонов. [manual] — нажато вручную: сообщает и о неудаче.
     */
    fun ensureToday(manual: Boolean = false) {
        if (!Modules.on("day")) return
        if (!manual && !autoDay) return
        val dao = dao() ?: return
        val today = java.time.LocalDate.now()
        if (!manual && autoDayTried == today) return
        viewModelScope.launch {
            val folders = dao.folderTitles().first()
            if (folders.any { dateInTitle(it.title) == today }) {
                autoDayTried = today
                if (manual) message = "Папка сегодняшнего дня уже есть"
                return@launch
            }
            val last = folders.filter { dateInTitle(it.title) != null }.maxByOrNull { dateInTitle(it.title)!! }
            if (last == null) {
                if (manual) message = "Сначала создайте одну папку дня вручную, например «${formatLike("", today)}»: по ней я пойму, где и в каком формате создавать"
                return@launch
            }
            autoDayTried = today
            val now = System.currentTimeMillis()
            val title = formatLike(last.title, today)
            val fid = dao.insert(
                NoteEntity(parentId = last.parentId, isFolder = true, title = title, sortIndex = dao.maxSort(last.parentId) + 1, createdAt = now, updatedAt = now),
            )
            val snippets = allSnippets.value.filter { it.label in autoDaySnips }
            snippets.forEachIndexed { i, s ->
                val (text, spans) = expandTemplate(s.text, s.spansJson, fid)
                dao.insert(
                    NoteEntity(
                        parentId = fid, title = expandSnippet(s.title), body = text, spansJson = spans,
                        sortIndex = i, createdAt = now, updatedAt = now,
                    ),
                )
            }
            message = "Создана папка дня «$title»" + if (snippets.isEmpty()) "" else " и ${snippets.size} зам."
        }
    }

    // ---------- голосовой ввод ----------

    data class VoiceDraft(
        val spoken: String, val title: String, val body: String,
        val folderId: Long?, val folderName: String, val viaAi: Boolean,
        val checklist: Boolean = false, val iconKey: String? = null, val iconColor: Int? = null, val remindAt: Long? = null,
    )

    var voiceDraft by mutableStateOf<VoiceDraft?>(null)
    var voiceBusy by mutableStateOf(false)
    val gigaActive: Boolean get() = gigaKey.isNotBlank() && Modules.on("giga")

    /** Включает или выключает модуль; для геозон сразу пересобираем регистрацию. */
    fun setModule(id: String, on: Boolean) {
        Modules.set(app, id, on)
        if (id == "geo") dao()?.let { d -> viewModelScope.launch { GeoSync.rebuild(app, if (on) d.withGeo().first() else emptyList()) } }
        if (id == "day" && on) ensureToday()
    }

    var gigaKey by mutableStateOf(uiPrefs().getString("gc_key", "") ?: "")
        private set
    var gigaTrustOffer by mutableStateOf<ru.stasoft.mynotes.data.CertOffer?>(null)

    fun updateGigaKey(k: String) {
        gigaKey = k.trim()
        uiPrefs().edit().putString("gc_key", gigaKey).apply()
    }

    private fun voiceTarget(): Triple<Long?, String, Long?> {
        val t = today.value
        return if (t != null) Triple(t.folder.id, t.folder.title, t.folder.id)
        else Triple(folderId.value, path.lastOrNull()?.title?.ifBlank { null } ?: "Корень", null)
    }

    /** Разбор по правилам; null — шаблон не найден или поля не заполнились (нужен ИИ или обычная заметка). */
    private suspend fun voiceByRules(spoken: String, parent: Long?, folderName: String): VoiceDraft? {
        val m = matchVoiceTemplate(spoken, allSnippets.value) ?: return null
        val (text, _) = expandTemplate(m.template.text, m.template.spansJson, parent)
        val title = expandSnippet(m.template.title).ifBlank { m.template.label }
        val keys = templateFieldKeys(text)
        val (body, filled) = fillTemplateFromSpeech(text, m.rest)
        if (keys.isNotEmpty() && filled == 0) return null
        val full = withCar(if (keys.isEmpty() && m.rest.isNotBlank()) body.trimEnd() + "\n" + m.rest else body, spoken)
        return VoiceDraft(spoken, title, full, parent, folderName, false)
    }

    private suspend fun voiceByAi(spoken: String, parent: Long?, folderName: String): VoiceDraft {
        val snippets = allSnippets.value
        val list = snippets.joinToString("\n") { sn ->
            val keys = templateFieldKeys(sn.text)
            "- ${sn.label}" + if (keys.isNotEmpty()) ": поля " + keys.joinToString(", ") else ""
        }
        val system = "Ты помощник приложения для заметок. Пользователь продиктовал заметку голосом. " +
            "Определи, подходит ли один из шаблонов, и извлеки значения полей. " +
            "Отвечай ТОЛЬКО одним JSON-объектом без пояснений и без markdown: " +
            "{\"template\":\"название шаблона или null\",\"title\":\"заголовок заметки\",\"fields\":{\"Поле\":\"значение\"},\"text\":\"остальной текст заметки или пустая строка\"}. " +
            "Числа пиши цифрами с десятичной запятой и без единиц измерения; рубли и копейки объединяй в одно число (75 рублей 65 копеек = 75,65). " +
            "Заполняй поля только тем, что прямо сказано, ничего не выдумывай и не вычисляй. " +
            "Если шаблон не подходит, template = null, а title и text составь из речи.\n" +
            "Шаблоны:\n" + list
        val ans = ru.stasoft.mynotes.data.GigaChat.chat(app, gigaKey, system, spoken)
        val json = org.json.JSONObject(ans.substring(ans.indexOf('{'), ans.lastIndexOf('}') + 1))
        val tname = json.optString("template", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
        val tpl = tname?.let { n -> snippets.firstOrNull { it.label.equals(n, true) } }
        val extra = json.optString("text", "").trim().takeIf { !it.equals("null", true) }.orEmpty()
        if (tpl != null) {
            val (text, _) = expandTemplate(tpl.text, tpl.spansJson, parent)
            val fields = json.optJSONObject("fields")
            val map = HashMap<String, String>()
            fields?.keys()?.forEach { k -> map[k] = fields.optString(k, "") }
            val (body, _) = fillTemplateFromMap(text, map)
            val title = expandSnippet(tpl.title).ifBlank { tpl.label }
            val full = if (extra.isNotBlank() && templateFieldKeys(text).none { it.contains("примеч", true) }) body.trimEnd() + "\n" + extra else body
            return VoiceDraft(spoken, title, withCar(full, spoken), parent, folderName, true)
        }
        val title = json.optString("title", "").trim().ifBlank { plainVoiceNote(spoken).first }
        return VoiceDraft(spoken, title, extra.ifBlank { plainVoiceNote(spoken).second }, parent, folderName, true)
    }

    /** Заметка-список: название — то, что сказано до «в виде списка», пункты — после. */
    private fun voiceList(cmds: ru.stasoft.mynotes.data.VoiceCommands, spoken: String, parent: Long?, folderName: String): VoiceDraft {
        val before = stripVoiceLead(cmds.listBefore.orEmpty())
        val after = cmds.listAfter.orEmpty()
        val title: String
        val itemsText: String
        if (after.isNotBlank()) {
            title = before.ifBlank { "Список" }
            itemsText = after
        } else {
            val c = before.indexOf(':')
            if (c > 0) { title = before.substring(0, c); itemsText = before.substring(c + 1) } else { title = "Список"; itemsText = before }
        }
        val items = splitListItems(itemsText)
        return VoiceDraft(spoken, title.trim().replaceFirstChar { it.uppercase() }, items.joinToString("\n"), parent, folderName, false, checklist = true)
    }

    /** Имя иконки: по словарю, а если слова незнакомые и есть ключ GigaChat — по подсказке модели. */
    private suspend fun voiceIcon(words: List<String>): String? {
        if (words.isEmpty()) return null
        resolveIconName(words) { Mdi.has(app, it) }?.let { return it }
        if (!gigaActive) return null
        return try {
            val a = ru.stasoft.mynotes.data.GigaChat.chat(
                app, gigaKey,
                "Подбери имя иконки из набора Material Design Icons (mdi, pictogrammers.com/library/mdi) по смыслу слов. " +
                    "Ответь только одним английским именем через дефис, например gas-station, без пояснений.",
                words.joinToString(" "),
            )
            val n = a.trim().lowercase().removePrefix("mdi-").removePrefix("mdi:").trim('"', '\'', '.', ' ', '`')
            n.takeIf { Mdi.has(app, it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Продиктованная фраза → черновик заметки в папке сегодняшнего дня (или в текущей, если такой папки нет). */
    fun voiceInput(spoken: String, forceAi: Boolean = false) {
        viewModelScope.launch {
            voiceBusy = true
            try {
                val (parent, folderName, _) = voiceTarget()
                val cmds = extractVoiceCommands(spoken)
                val work = cmds.text
                var draft = if (forceAi) null else voiceByRules(work, parent, folderName)
                if (draft == null && cmds.isList) draft = voiceList(cmds, spoken, parent, folderName)
                if (draft == null && gigaActive) {
                    try {
                        draft = voiceByAi(work, parent, folderName)
                    } catch (e: javax.net.ssl.SSLException) {
                        message = "Нужно разрешить доверие сертификату Минцифры: Настройки → Голосовой ввод"
                    } catch (e: Exception) {
                        message = "ИИ не ответил: ${e.message?.take(120)}"
                    }
                }
                if (draft == null) {
                    val (t, b) = plainVoiceNote(work)
                    draft = VoiceDraft(spoken, t, b, parent, folderName, false)
                }
                val icon = cmds.iconWords?.let { voiceIcon(it) }
                voiceDraft = draft.copy(spoken = spoken, iconKey = icon, iconColor = cmds.color, remindAt = cmds.remindAt)
            } finally {
                voiceBusy = false
            }
        }
    }

    fun voiceCommit(d: VoiceDraft, title: String, body: String, asList: Boolean = d.checklist) {
        val dao = dao() ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val id = dao.insert(
                NoteEntity(
                    parentId = d.folderId, title = title.trim(), body = body,
                    sortIndex = dao.maxSort(d.folderId) + 1, createdAt = now, updatedAt = now,
                    checklist = asList, iconKey = d.iconKey, iconColorArgb = d.iconColor, reminderAt = d.remindAt,
                ),
            )
            selectedId = id
            voiceDraft = null
            message = "Заметка создана в «${d.folderName}»"
        }
    }

    /** Проверка подключения к GigaChat; при ошибке сертификата предлагает доверять корневому сертификату Минцифры. */
    fun gigaCheck() {
        viewModelScope.launch {
            voiceBusy = true
            try {
                val a = ru.stasoft.mynotes.data.GigaChat.chat(app, gigaKey, "Отвечай одним словом.", "Скажи: работает")
                message = "GigaChat подключён: ${a.take(40)}"
            } catch (e: javax.net.ssl.SSLException) {
                try {
                    gigaTrustOffer = ru.stasoft.mynotes.data.GigaChat.fetchChain()
                } catch (e2: Exception) {
                    message = "Не удалось связаться с сервером Сбера: ${e2.message?.take(100)}"
                }
            } catch (e: Exception) {
                message = "Ошибка: ${e.message?.take(150)}"
            } finally {
                voiceBusy = false
            }
        }
    }

    fun gigaTrust(offer: ru.stasoft.mynotes.data.CertOffer) {
        ru.stasoft.mynotes.data.GigaChat.saveTrusted(app, offer.der)
        gigaTrustOffer = null
        gigaCheck()
    }

    /** Кнопка «+»: новая пустая заметка в текущей папке, сразу открывается редактор. */
    fun newNote() {
        val dao = dao() ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val parent = folderId.value
            val id = dao.insert(
                NoteEntity(parentId = parent, sortIndex = dao.maxSort(parent) + 1, createdAt = now, updatedAt = now),
            )
            justCreated = Created(id, parent, true)
            selectedId = id
            dao.get(id)?.let {
                editing = it
                loadChain(it)
            }
        }
    }

    /**
     * Кнопка «дочерняя заметка»: новая заметка внутри выбранной (она становится папкой).
     * Если ничего не выбрано, создаётся пустая папка в текущем месте.
     */
    fun newChild() {
        val dao = dao() ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val sel = selectedId?.let { dao.get(it) }
            val created: Created
            val id: Long
            if (sel == null) {
                val parent = folderId.value
                id = dao.insert(
                    NoteEntity(parentId = parent, isFolder = true, sortIndex = dao.maxSort(parent) + 1, createdAt = now, updatedAt = now),
                )
                created = Created(id, parent, true)
            } else {
                id = dao.insert(
                    NoteEntity(parentId = sel.id, sortIndex = dao.maxSort(sel.id) + 1, createdAt = now, updatedAt = now),
                )
                if (!sel.isFolder) dao.update(sel.copy(isFolder = true, updatedAt = now))
                created = Created(id, sel.id, sel.isFolder)
            }
            justCreated = created
            dao.get(id)?.let {
                editing = it
                loadChain(it)
            }
        }
    }

    /** Кнопка «Редактировать»: правка выделенной записи (заметки или папки). */
    fun editSelected() {
        val dao = dao() ?: return
        val id = selectedId
        if (id == null) {
            // Ничего не выбрано — действие относится к текущей папке (её строка выделена сверху).
            val cur = path.lastOrNull()
            if (cur == null) message = "Сначала выберите запись: коснитесь её" else editNote(cur)
            return
        }
        viewModelScope.launch {
            val n = dao.get(id)
            if (n == null) message = "Запись не найдена" else editNote(n)
        }
    }

    /** Двойной тап — редактирование записи (в том числе папки). */
    /** Запись, чьи свойства открыты (поверх редактора). */
    var props by mutableStateOf<NoteEntity?>(null)
        private set

    /** Переменные, которые папка получает от родителей (без собственных), с названием папки-источника. */
    suspend fun inheritedVars(note: NoteEntity): List<Pair<VarDef, String>> {
        val dao = dao() ?: return emptyList()
        val chain = mutableListOf<NoteEntity>()
        var cur = note.parentId
        while (cur != null && chain.size < 64) {
            val n = dao.get(cur) ?: break
            chain.add(n)
            cur = n.parentId
        }
        val result = mutableListOf<Pair<VarDef, String>>()
        for (f in chain.asReversed()) {
            for (d in parseVars(f.varsJson)) {
                val i = result.indexOfFirst { it.first.name == d.name }
                if (d.off) {
                    if (i >= 0) result.removeAt(i)
                } else if (i >= 0) {
                    result[i] = d to f.title.ifBlank { "…" }
                } else {
                    result.add(d to f.title.ifBlank { "…" })
                }
            }
        }
        return result
    }

    /** Проверка показаний поля [field] внутри текущей папки (в корне — по всей базе). */
    suspend fun checkReadings(field: String): List<ReadingIssue> {
        val dao = dao() ?: return emptyList()
        val rows = dao.titleRows()
        val scope = folderId.value
        return withContext(Dispatchers.Default) { ru.stasoft.mynotes.data.checkReadings(rows, scope, field) }
    }

    /** Открыть заметку по id (из списка проверки). */
    fun openById(id: Long) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val n = dao.get(id) ?: return@launch
            // Переходим в папку заметки, чтобы после выхода из редактора она была на виду.
            query.value = ""
            folderId.value = n.parentId
            refreshPath()
            editNote(n)
        }
    }

    /** Включает или выключает режим списка задач у открытой записи. */
    fun setChecklist(id: Long, on: Boolean) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val n = dao.get(id) ?: return@launch
            dao.update(n.copy(checklist = on, spansJson = if (on) "" else n.spansJson))
            if (editing?.id == id) editing = dao.get(id)
        }
    }

    fun openProps(note: NoteEntity) {
        val dao = dao() ?: return
        viewModelScope.launch { props = dao.get(note.id) ?: note }
    }

    /** Сохраняет свойства и возвращает в редактор со свежими данными из базы. */
    fun closeProps(change: ((NoteEntity) -> NoteEntity)?) {
        val dao = dao()
        val id = props?.id
        props = null
        if (dao == null || id == null) return
        viewModelScope.launch {
            val fresh = dao.get(id)
            if (fresh != null && change != null) {
                val n = change(fresh)
                if (n != fresh) dao.update(n.copy(updatedAt = System.currentTimeMillis()))
            }
            editing = dao.get(id)
        }
    }

    fun editNote(note: NoteEntity) {
        justCreated = null
        selectedId = note.id
        editing = note
        loadChain(note)
    }

    fun openNote(note: NoteEntity) {
        if (note.isFolder) {
            openFolder(note)
        } else {
            editNote(note)
        }
    }

    /**
     * Закрывает редактор. Если передать текущие [title] и [body] и запись создана только что и осталась пустой,
     * она удаляется (чтобы не копились пустые заметки).
     */
    fun closeEditor(title: String? = null, body: String? = null) {
        val dao = dao()
        val cur = editing
        val jc = justCreated
        if (cur != null && cur.id < 0) {
            // Закрыли правку шаблона: возвращаемся к списку шаблонов.
            editing = null
            justCreated = null
            reopenTemplates = true
            return
        }
        if (cur != null) {
            selectedId = cur.id
            scrollTick++
        }
        editing = null
        justCreated = null
        if (dao == null || cur == null || jc == null || jc.id != cur.id) return
        if (title == null || body == null || title.isNotBlank() || body.isNotBlank()) return
        viewModelScope.launch {
            val n = dao.get(jc.id) ?: return@launch
            if (n.reminderAt != null || dao.childCount(n.id) > 0) return@launch
            dao.delete(n)
            if (selectedId == n.id) selectedId = null
            val pid = jc.parentId
            if (pid != null && !jc.parentWasFolder && dao.childCount(pid) == 0) {
                dao.get(pid)?.let { dao.update(it.copy(isFolder = false)) }
            }
        }
    }

    fun save(id: Long, title: String, body: String, spans: String? = null) {
        val dao = dao() ?: return
        viewModelScope.launch {
            if (id < 0) {
                // Правка шаблона: «заметка» с отрицательным id — это шаблон -id.
                val sn = allSnippets.value.firstOrNull { it.id == -id } ?: return@launch
                dao.updateSnippet(sn.copy(title = title, text = body, spansJson = spans ?: sn.spansJson))
                editing?.let { if (it.id == id) editing = it.copy(title = title, body = body, spansJson = spans ?: sn.spansJson) }
                return@launch
            }
            val cur = dao.get(id) ?: return@launch
            val sp = spans ?: cur.spansJson
            if (cur.title == title && cur.body == body && cur.spansJson == sp) return@launch
            dao.update(cur.copy(title = title, body = body, spansJson = sp, updatedAt = System.currentTimeMillis()))
            if (editing?.id == id) editing = dao.get(id)
        }
    }

    fun rename(note: NoteEntity, title: String) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.update(note.copy(title = title.trim(), updatedAt = System.currentTimeMillis())) }
    }

    /** Выносит содержимое папки на уровень выше или в корень базы и удаляет опустевшую папку. */
    fun flattenFolder(folder: NoteEntity, toRoot: Boolean = false) {
        val db = dbFlow.value ?: return
        viewModelScope.launch {
            try {
                db.withTransaction {
                    val dao = db.notes()
                    val kids = dao.children(folder.id).first()
                    val target = if (toRoot) null else folder.parentId
                    var pos = dao.maxSort(target) + 1
                    kids.forEach { k -> dao.update(k.copy(parentId = target, sortIndex = pos++)) }
                    dao.get(folder.id)?.let { dao.delete(it) }
                }
                message = if (toRoot) "Содержимое папки перенесено в корень" else "Содержимое папки перенесено наверх"
            } catch (e: Exception) {
                message = "Не удалось перенести: ${e.message}"
            }
        }
    }

    // ---------- копирование и перенос ----------

    /** Запись, выбранная для переноса или копирования; вставляется кнопкой «Вставить сюда». */
    data class Clip(val id: Long, val title: String, val move: Boolean)

    var clip by mutableStateOf<Clip?>(null)
        private set

    fun startClip(note: NoteEntity, move: Boolean) {
        clip = Clip(note.id, note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty().take(40) }.ifBlank { "Без названия" }, move)
    }

    fun cancelClip() {
        clip = null
    }

    /** Вставляет выбранную запись (папку — со всем содержимым) в открытую сейчас папку. */
    fun pasteHere() {
        val c = clip ?: return
        val db = dbFlow.value ?: return
        val target = folderId.value
        viewModelScope.launch {
            try {
                val dao = db.notes()
                var cur = target
                while (cur != null) {
                    if (cur == c.id) {
                        message = "Нельзя вставить папку внутрь самой себя"
                        return@launch
                    }
                    cur = dao.get(cur)?.parentId
                }
                val src = dao.get(c.id)
                if (src == null) {
                    message = "Исходная запись не найдена"
                    clip = null
                    return@launch
                }
                var resultId = src.id
                if (c.move) {
                    if (src.parentId == target) {
                        message = "Запись уже лежит в этой папке"
                        clip = null
                        return@launch
                    }
                    db.withTransaction {
                        dao.update(src.copy(parentId = target, sortIndex = dao.maxSort(target) + 1))
                        target?.let { t -> dao.get(t)?.let { if (!it.isFolder) dao.update(it.copy(isFolder = true)) } }
                    }
                    message = "Перемещено"
                } else {
                    val snippets = allSnippets.value
                    val sameParent = src.parentId == target
                    var count = 0
                    suspend fun copyTree(n: NoteEntity, parent: Long?, pos: Int, top: Boolean): Long {
                        val kids = if (n.isFolder) dao.children(n.id).first() else emptyList()
                        val id = dao.insert(
                            n.copy(
                                id = 0,
                                parentId = parent,
                                sortIndex = pos,
                                legacyId = null,
                                // Напоминания у копии не дублируем, чтобы не приходили двойные уведомления.
                                reminderAt = null,
                                reminderDoneAt = null,
                                title = if (top && sameParent) n.title + " (копия)" else n.title,
                            ),
                        )
                        count++
                        snippets.filter { it.folderId == n.id }.forEach { dao.insertSnippet(it.copy(id = 0, folderId = id)) }
                        kids.forEachIndexed { i, k -> copyTree(k, id, i, false) }
                        return id
                    }
                    db.withTransaction {
                        resultId = copyTree(src, target, dao.maxSort(target) + 1, true)
                        target?.let { t -> dao.get(t)?.let { if (!it.isFolder) dao.update(it.copy(isFolder = true)) } }
                    }
                    message = "Скопировано записей: $count"
                }
                clip = null
                selectedId = resultId
                scrollTick++
            } catch (e: Exception) {
                message = "Не получилось: ${e.message}"
            }
        }
    }

    fun delete(note: NoteEntity) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.delete(note) }
    }

    // ---------- импорт ----------

    fun importMtnt(uri: Uri) {
        val db = dbFlow.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw MtntImporter.ImportException("Не удалось открыть файл")
                    val roots = MtntImporter.parse(bytes)
                    val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                    MtntImporter.importInto(db, roots, null)
                }
                message = "Импортировано записей: $count"
            } catch (e: MtntImporter.ImportException) {
                message = e.message
            } catch (e: Exception) {
                message = "Ошибка импорта: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val AUTO_LOCK_MS = 30_000L
        const val REQUEST_TEMPLATE_LABEL = "Заявка (поля)"
        const val REQUEST_TEMPLATE =
            "Договор: \nТелефон: +7\nРабота: \nСкорость мб: \nПримечание: \nwf name: \nwf pass: \n" +
                "Кабель вп: \nКабель кокс: \nСвитч: \nПорт: \n{переменные}"
    }
}
