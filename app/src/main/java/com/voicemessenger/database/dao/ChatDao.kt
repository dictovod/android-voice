package com.voicemessenger.database.dao

import androidx.room.*
import com.voicemessenger.database.entities.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    
    @Query("SELECT * FROM chats ORDER BY last_message_time DESC")
    fun getAllChats(): Flow<List<Chat>>
    
    @Query("SELECT * FROM chats WHERE chat_id = :chatId")
    suspend fun getChatById(chatId: String): Chat?
    
    @Query("SELECT * FROM chats WHERE participant_id = :participantId")
    suspend fun getChatByParticipant(participantId: String): Chat?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)
    
    @Update
    suspend fun updateChat(chat: Chat)
    
    @Delete
    suspend fun deleteChat(chat: Chat)
    
    @Query("UPDATE chats SET last_message = :message, last_message_time = :time WHERE chat_id = :chatId")
    suspend fun updateLastMessage(chatId: String, message: String, time: Long)
    
    @Query("UPDATE chats SET unread_count = :count WHERE chat_id = :chatId")
    suspend fun updateUnreadCount(chatId: String, count: Int)
}