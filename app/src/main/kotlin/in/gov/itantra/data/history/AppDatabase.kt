package `in`.gov.itantra.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [HistoryMessage::class], version = 1, exportSchema = false)
@TypeConverters(HistoryTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
