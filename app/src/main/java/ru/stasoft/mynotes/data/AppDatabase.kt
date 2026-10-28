package ru.stasoft.mynotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [NoteEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN sortMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderPeriod INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderCheckDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderDoneAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN flags INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN mapJson TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Открывает зашифрованную базу. [key] — мастер-ключ из Vault; вызывающий затирает его сам. */
        fun open(context: Context, key: ByteArray): AppDatabase {
            val factory = SupportOpenHelperFactory(key.copyOf())
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "notes.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
