package com.voicemessenger.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    
    @ColumnInfo(name = "sender_id")
    val senderId: String,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "is_from_me")
    val isFromMe: Boolean,
    
    @ColumnInfo(name = "message_type")
    val messageType: MessageType = MessageType.TEXT,
    
    @ColumnInfo(name = "voice_file_path")
    val voiceFilePath: String? = null,
    
    @ColumnInfo(name = "voice_duration")
    val voiceDuration: Int? = null,
    
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false
)

enum class MessageType {
    TEXT,
    VOICE,
    IMAGE
}
