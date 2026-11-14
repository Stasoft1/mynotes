package ru.stasoft.mynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Фото заметки. Сам снимок лежит отдельным зашифрованным файлом [file] (см. PhotoStore), в базе только ссылка. */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val file: String,
    val createdAt: Long,
)

data class PhotoCount(val noteId: Long, val n: Int)
