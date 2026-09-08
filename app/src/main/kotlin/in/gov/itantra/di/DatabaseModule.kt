package `in`.gov.itantra.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.data.history.AppDatabase
import `in`.gov.itantra.data.history.HistoryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "itantra_database"
        ).build()
    }

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }
}
