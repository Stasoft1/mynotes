package ru.stasoft.mynotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    // Ручной порядок: как сохранён в sortIndex. Автосортировка по режиму папки делается в ViewModel.
    @Query("SELECT * FROM notes WHERE parentId IS :parentId ORDER BY sortIndex ASC, id ASC")
    fun children(parentId: Long?): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' " +
            "ORDER BY updatedAt DESC LIMIT 200",
    )
    fun search(q: String): Flow<List<NoteEntity>>

    /** Заметки с активными напоминаниями: повторяющиеся и ещё не выполненные разовые. */
    @Query(
        "SELECT * FROM notes WHERE reminderAt IS NOT NULL AND (reminderPeriod > 0 OR reminderDoneAt IS NULL) " +
            "ORDER BY reminderAt ASC",
    )
    fun withReminders(): Flow<List<NoteEntity>>

    @Query("SELECT id, parentId, isFolder, varsJson, substr(body, 1, 2000) AS head FROM notes")
    suspend fun numRows(): List<NumRow>

    @Query("SELECT id, parentId, isFolder, title, substr(body, 1, 2000) AS head FROM notes")
    suspend fun titleRows(): List<TitleRow>

    /** Меняется при любой правке, добавлении и удалении записей: по нему пересчитываются суммы. */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) || ':' || COUNT(*) || ':' || COALESCE(SUM(LENGTH(varsJson)), 0) FROM notes")
    fun changeStamp(): Flow<String>

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM notes WHERE parentId IS :parentId")
    suspend fun maxSort(parentId: Long?): Int

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE parentId = :id")
    suspend fun childCount(id: Long): Int

    @Query("SELECT * FROM snippets ORDER BY sortIndex ASC, id ASC")
    fun allSnippets(): Flow<List<SnippetEntity>>

    @Query("SELECT COUNT(*) FROM snippets")
    suspend fun snippetCount(): Int

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM snippets")
    suspend fun maxSnippetSort(): Int

    @Insert
    suspend fun insertSnippet(s: SnippetEntity): Long

    @Update
    suspend fun updateSnippet(s: SnippetEntity)

    @Delete
    suspend fun deleteSnippet(s: SnippetEntity)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}
