package com.voicemessenger.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    
    @ColumnInfo(name = "participant_id")
    val participantId: String,
    
    @ColumnInfo(name = "participant_name")
    val participantName: String,
    
    @ColumnInfo(name = "participant_avatar")
    val participantAvatar: String? = null,
    
    @ColumnInfo(name = "last_message")
    val lastMessage: String = "",
    
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,
    
    @ColumnInfo(name = "is_online")
    val isOnline: Boolean = false
)