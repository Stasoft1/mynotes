package ru.stasoft.mynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Сохранённое место (магазин, дом, работа) для быстрого выбора в напоминаниях по месту. */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radius: Int = 200,
)
