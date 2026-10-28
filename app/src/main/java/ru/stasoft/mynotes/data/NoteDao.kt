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

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM notes WHERE parentId IS :parentId")
    suspend fun maxSort(parentId: Long?): Int

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}
