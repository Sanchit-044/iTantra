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

    @Query("DELETE FROM history_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("UPDATE history_messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: MessageStatus)

    @Query("UPDATE history_messages SET status = :status, peerName = :peerName, distanceMeters = COALESCE(:distanceMeters, distanceMeters) WHERE id = :id")
    suspend fun updateMessageStatusAndPeer(id: String, status: MessageStatus, peerName: String?, distanceMeters: Float? = null)

    @Query("UPDATE history_messages SET status = :status, peerName = CASE WHEN peerName IS NULL OR peerName = '' THEN :peerName WHEN peerName LIKE '%' || :peerName || '%' THEN peerName ELSE peerName || ', ' || :peerName END, distanceMeters = COALESCE(:distanceMeters, distanceMeters), locationLabel = COALESCE(:locationLabel, locationLabel) WHERE id = :id")
    suspend fun addPeerToMessage(id: String, status: MessageStatus, peerName: String, distanceMeters: Float? = null, locationLabel: String? = null)
}
