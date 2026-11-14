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

    /** Все записи для поиска без учёта регистра (SQLite LIKE не умеет в кириллицу). */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun allNotes(): Flow<List<NoteEntity>>

    /** Заметки с активными напоминаниями: повторяющиеся и ещё не выполненные разовые. */
    @Query(
        "SELECT * FROM notes WHERE reminderAt IS NOT NULL AND (reminderPeriod > 0 OR reminderDoneAt IS NULL) " +
            "ORDER BY reminderAt ASC",
    )
    fun withReminders(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun places(): Flow<List<PlaceEntity>>

    @Insert
    suspend fun insertPlace(p: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    /** Заметки с напоминанием по месту. */
    @Query("SELECT * FROM notes WHERE geoLat IS NOT NULL AND geoLon IS NOT NULL")
    fun withGeo(): Flow<List<NoteEntity>>

    @Query("SELECT id, parentId, isFolder, title, varsJson, substr(body, 1, 2000) AS head FROM notes")
    suspend fun numRows(): List<NumRow>

    @Query("SELECT id, parentId, isFolder, title, substr(body, 1, 2000) AS head FROM notes")
    suspend fun titleRows(): List<TitleRow>

    @Query("SELECT id, parentId, title FROM notes WHERE isFolder = 1")
    fun folderTitles(): Flow<List<FolderTitle>>

    /** Меняется при любой правке, добавлении и удалении записей: по нему пересчитываются суммы. */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) || ':' || COUNT(*) || ':' || COALESCE(SUM(LENGTH(varsJson)), 0) FROM notes")
    fun changeStamp(): Flow<String>

    @Query("UPDATE notes SET sortIndex = :index WHERE id = :id")
    suspend fun setSort(id: Long, index: Int)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM notes WHERE parentId IS :parentId")
    suspend fun maxSort(parentId: Long?): Int

    @Query("SELECT * FROM notes WHERE parentId IS :parentId ORDER BY sortIndex ASC, id ASC")
    suspend fun childrenOnce(parentId: Long?): List<NoteEntity>

    @Query("SELECT * FROM photos WHERE noteId = :noteId ORDER BY id")
    fun photosFor(noteId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT noteId, COUNT(*) AS n FROM photos GROUP BY noteId")
    fun photoCounts(): Flow<List<PhotoCount>>

    @Insert
    suspend fun insertPhoto(p: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("SELECT file FROM photos")
    suspend fun photoFiles(): List<String>

    /** Фото удалённых записей (в том числе вместе с папкой). */
    @Query("DELETE FROM photos WHERE noteId NOT IN (SELECT id FROM notes)")
    suspend fun deleteOrphanPhotos()

    @Query("SELECT * FROM cars ORDER BY sortIndex, id")
    fun cars(): Flow<List<CarEntity>>

    @Insert
    suspend fun insertCar(c: CarEntity): Long

    @Update
    suspend fun updateCar(c: CarEntity)

    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun deleteCar(id: Long)

    @Query("DELETE FROM service_items WHERE carId = :carId")
    suspend fun deleteServiceItemsOf(carId: Long)

    @Query("UPDATE cars SET isDefault = (id = :id)")
    suspend fun setDefaultCar(id: Long)

    @Query("SELECT * FROM service_items ORDER BY carId, id")
    fun serviceItems(): Flow<List<ServiceItemEntity>>

    @Insert
    suspend fun insertServiceItem(s: ServiceItemEntity): Long

    @Update
    suspend fun updateServiceItem(s: ServiceItemEntity)

    @Query("DELETE FROM service_items WHERE id = :id")
    suspend fun deleteServiceItem(id: Long)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE parentId = :id")
    suspend fun childCount(id: Long): Int

    @Query("SELECT * FROM snippets ORDER BY sortIndex ASC, id ASC")
    fun allSnippets(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets")
    suspend fun allSnippetsOnce(): List<SnippetEntity>

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
