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
    /**
     * Режим сортировки содержимого папки (как в MyTreeNotes): 0 — вручную (порядок из sortIndex),
     * 1/2 — по названию, 3/4 — по названию с конца, 5/6 — списки задач, 7/8 — по дате создания,
     * 9/10 — по напоминаниям.
     */
    val sortMode: Int = 0,
    /** Повтор напоминания: 0 — разовое, 1 — раз в неделю, 2 — месяц, 3 — квартал, 4 — полгода, 5 — год. */
    val reminderPeriod: Int = 0,
    /** За сколько дней до даты напоминать. */
    val reminderCheckDays: Int = 0,
    /** Когда напоминание отмечено выполненным (мс), если отмечено. */
    val reminderDoneAt: Long? = null,
    /** Сырые флаги и дополнительные данные из MyTreeNotes (иконки, цвета, теги), чтобы ничего не потерять. */
    val flags: Int = 0,
    val mapJson: String = "",
    /** Цвет текста строки в списке (ARGB); фон — в [colorArgb]. */
    val textColorArgb: Int? = null,
    /** Запрет редактирования записи. */
    val readOnly: Boolean = false,
    /** Показывать папки сверху при автосортировке содержимого этой папки. */
    val foldersFirst: Boolean = true,
    /** Текст показывается как список задач (чекбоксы). */
    val checklist: Boolean = false,
    /** Оформление текста (жирный, цвет, размер, выравнивание…) отдельно от самого текста, см. Spans.kt. */
    val spansJson: String = "",
    /** Переменные папки (JSON, см. Vars.kt): действуют на папку и все вложенные. */
    val varsJson: String = "",
    /** Устарело: заменено переменными (при обновлении базы перенесено в [varsJson]). */
    /** «Числовые значения»: числа в заметках этой папки (и вложенных) суммируются в её строке. */
    val numeric: Boolean = false,
    /** Откуда брать число: пусто — первая строка заметки, иначе название поля («Сумма»). */
    val numericField: String = "",
)
