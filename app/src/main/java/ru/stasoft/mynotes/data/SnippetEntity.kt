package ru.stasoft.mynotes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Быстрая вставка (кусок текста для кнопки над клавиатурой).
 * [folderId] = null — общая вставка для всех папок; иначе действует в этой папке и во всех вложенных.
 */
@Entity(
    tableName = "snippets",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val label: String,
    val text: String,
    val sortIndex: Int = 0,
)
