package ru.stasoft.mynotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [NoteEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        /** Открывает зашифрованную базу. [key] — мастер-ключ из Vault; вызывающий затирает его сам. */
        fun open(context: Context, key: ByteArray): AppDatabase {
            val factory = SupportOpenHelperFactory(key.copyOf())
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "notes.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
