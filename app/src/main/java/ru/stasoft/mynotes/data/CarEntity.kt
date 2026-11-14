package ru.stasoft.mynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Машина из справочника модуля «Авто». [aliases] — короткие имена для голоса через запятую. */
@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val aliases: String = "",
    val isDefault: Boolean = false,
    val sortIndex: Int = 0,
)

/**
 * Регламент ТО машины: работа и интервалы. Пробег и дата последнего выполнения берутся из заметок
 * (поле «Работа:» с названием работы), здесь только правила. 0 — интервал не задан.
 */
@Entity(tableName = "service_items")
data class ServiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Long,
    val name: String,
    val everyKm: Int = 0,
    val everyMonths: Int = 0,
)
