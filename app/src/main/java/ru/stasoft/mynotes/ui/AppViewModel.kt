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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlinx.coroutines.Job
import ru.stasoft.mynotes.reminders.ReminderLogic
import ru.stasoft.mynotes.reminders.ReminderSync
import ru.stasoft.mynotes.crypto.BiometricVault
import ru.stasoft.mynotes.crypto.Vault
import ru.stasoft.mynotes.data.AppDatabase
import ru.stasoft.mynotes.data.DEFAULT_SNIPPETS
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.SnippetEntity
import ru.stasoft.mynotes.data.sortNotes
import ru.stasoft.mynotes.importer.MtntImporter

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

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
    var editing by mutableStateOf<NoteEntity?>(null)
        private set

    /** Цепочка папок от корня до текущей. */
    var path by mutableStateOf<List<NoteEntity>>(emptyList())
        private set

    private val dbFlow = MutableStateFlow<AppDatabase?>(null)
    val folderId = MutableStateFlow<Long?>(null)
    val query = MutableStateFlow("")

    /** Режим сортировки текущей папки (у корня — ручной). */
    val sortMode = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<NoteEntity>> =
        combine(dbFlow, folderId, query, sortMode) { db, fid, q, mode -> listOf(db, fid, q, mode) }
            .flatMapLatest { args ->
                val db = args[0] as AppDatabase?
                val fid = args[1] as Long?
                val q = args[2] as String
                val mode = args[3] as Int
                when {
                    db == null -> flowOf(emptyList())
                    q.isBlank() -> db.notes().children(fid).map { sortNotes(it, mode) }
                    else -> db.notes().search(q.trim())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var backgroundedAt = 0L

    // ---------- быстрые вставки ----------

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
        if (prefs.getBoolean("snippets_seeded", false)) return
        val dao = db.notes()
        if (dao.snippetCount() == 0) {
            DEFAULT_SNIPPETS.forEachIndexed { i, (label, text) ->
                dao.insertSnippet(SnippetEntity(folderId = null, label = label, text = text, sortIndex = i))
            }
        }
        prefs.edit().putBoolean("snippets_seeded", true).apply()
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
            dbFlow.value = db
            needSetup = false
            unlocked = true
            error = null
            refreshPath()
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

    /** Режим сортировки текущей папки (см. NoteEntity.sortMode). В корне — только ручной порядок. */
    fun setSortMode(mode: Int) {
        val dao = dao() ?: return
        val id = folderId.value
        if (id == null) {
            message = "Корень сортируется вручную. Откройте папку, чтобы выбрать сортировку"
            return
        }
        viewModelScope.launch {
            val f = dao.get(id) ?: return@launch
            dao.update(f.copy(sortMode = mode))
            sortMode.value = mode
            path = path.map { if (it.id == id) it.copy(sortMode = mode) else it }
        }
    }

    fun lock() {
        val db = dbFlow.value
        reminderJob?.cancel()
        reminderJob = null
        dbFlow.value = null
        unlocked = false
        showReminders = false
        editing = null
        path = emptyList()
        folderId.value = null
        sortMode.value = 0
        query.value = ""
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

    fun goToPathIndex(index: Int) {
        // index = -1 — корень
        selectedId = null
        folderId.value = if (index < 0) null else path[index].id
        refreshPath()
    }

    fun goUp(): Boolean {
        if (editing != null) {
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
        selectedId = null
        folderId.value = path.getOrNull(path.size - 2)?.id
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
            sortMode.value = path.lastOrNull()?.sortMode ?: 0
        }
    }

    // ---------- записи ----------

    /** Выбранная в списке запись (одиночный тап): на неё действуют кнопки панели. */
    var selectedId by mutableStateOf<Long?>(null)
        private set

    fun select(note: NoteEntity) {
        selectedId = note.id
    }

    /** Запись, созданная в этой сессии редактора: если её оставили пустой, она удаляется при выходе. */
    private data class Created(val id: Long, val parentId: Long?, val parentWasFolder: Boolean)

    private var justCreated: Created? = null

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
            message = "Сначала выберите запись: коснитесь её"
            return
        }
        viewModelScope.launch {
            val n = dao.get(id)
            if (n == null) message = "Запись не найдена" else editNote(n)
        }
    }

    /** Двойной тап — редактирование записи (в том числе папки). */
    fun editNote(note: NoteEntity) {
        justCreated = null
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
        editing = null
        justCreated = null
        if (dao == null || cur == null || jc == null || jc.id != cur.id) return
        if (title == null || body == null || title.isNotBlank() || body.isNotBlank()) return
        viewModelScope.launch {
            val n = dao.get(jc.id) ?: return@launch
            if (n.reminderAt != null || dao.childCount(n.id) > 0) return@launch
            dao.delete(n)
            val pid = jc.parentId
            if (pid != null && !jc.parentWasFolder && dao.childCount(pid) == 0) {
                dao.get(pid)?.let { dao.update(it.copy(isFolder = false)) }
            }
        }
    }

    fun save(id: Long, title: String, body: String) {
        val dao = dao() ?: return
        viewModelScope.launch {
            val cur = dao.get(id) ?: return@launch
            if (cur.title == title && cur.body == body) return@launch
            dao.update(cur.copy(title = title, body = body, updatedAt = System.currentTimeMillis()))
        }
    }

    fun rename(note: NoteEntity, title: String) {
        val dao = dao() ?: return
        viewModelScope.launch { dao.update(note.copy(title = title.trim(), updatedAt = System.currentTimeMillis())) }
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
                    MtntImporter.importInto(db, roots, "Импорт MyTreeNotes $stamp")
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
    }
}
