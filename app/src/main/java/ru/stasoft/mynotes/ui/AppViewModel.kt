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
import ru.stasoft.mynotes.data.NoteEntity
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
    private val sortMode = MutableStateFlow(0)

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
            ReminderSync.applyPending(app, db.notes())
            db.notes().withReminders().collect { ReminderSync.rebuild(app, it) }
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
        query.value = ""
        folderId.value = note.id
        refreshPath()
    }

    fun goToPathIndex(index: Int) {
        // index = -1 — корень
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

    fun create(title: String, isFolder: Boolean) {
        val dao = dao() ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val id = dao.insert(
                NoteEntity(
                    parentId = folderId.value,
                    title = title.trim(),
                    isFolder = isFolder,
                    sortIndex = dao.maxSort(folderId.value) + 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (!isFolder) editing = dao.get(id)
        }
    }

    fun openNote(note: NoteEntity) {
        if (note.isFolder) openFolder(note) else editing = note
    }

    fun closeEditor() {
        editing = null
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
