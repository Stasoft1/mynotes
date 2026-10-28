package ru.stasoft.mynotes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val title: String = "",
    val body: String = "",
    val isFolder: Boolean = false,
    val sortIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val reminderAt: Long? = null,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val keyword: String = "",
    /** id из файла MyTreeNotes, чтобы импорт можно было сверить с оригиналом. */
    val legacyId: String? = null,
)
