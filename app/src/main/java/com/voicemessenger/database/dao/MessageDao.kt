package com.voicemessenger.database.dao

import androidx.room.*
import com.voicemessenger.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
    
    @Update
    suspend fun updateMessage(message: Message)
    
    @Delete
    suspend fun deleteMessage(message: Message)
    
    @Query("UPDATE messages SET is_read = 1 WHERE chat_id = :chatId AND is_from_me = 0")
    suspend fun markChatMessagesAsRead(chatId: String)
    
    @Query("SELECT COUNT(*) FROM messages WHERE chat_id = :chatId AND is_read = 0 AND is_from_me = 0")
    suspend fun getUnreadCount(chatId: String): Int
    
    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)
}