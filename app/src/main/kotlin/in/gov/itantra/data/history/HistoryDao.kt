package `in`.gov.itantra.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: HistoryMessage)

    @Query("SELECT * FROM history_messages ORDER BY timestampMs DESC")
    fun getAllMessages(): Flow<List<HistoryMessage>>

    @Query("SELECT * FROM history_messages WHERE direction = :direction ORDER BY timestampMs DESC")
    fun getMessagesByDirection(direction: MessageDirection): Flow<List<HistoryMessage>>

    @Query("SELECT * FROM history_messages WHERE status = :status ORDER BY timestampMs DESC")
    fun getMessagesByStatus(status: MessageStatus): Flow<List<HistoryMessage>>
    
    @Query("DELETE FROM history_messages")
    suspend fun clearHistory()
}
