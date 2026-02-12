package com.voicemessenger.database

import androidx.room.TypeConverter
import com.voicemessenger.model.MessageType

class Converters {
    
    @TypeConverter
    fun fromMessageType(messageType: MessageType): String {
        return messageType.name
    }
    
    @TypeConverter
    fun toMessageType(messageType: String): MessageType {
        return MessageType.valueOf(messageType)
    }
}