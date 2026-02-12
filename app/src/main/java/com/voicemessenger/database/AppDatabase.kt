package com.voicemessenger.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.voicemessenger.database.dao.ChatDao
import com.voicemessenger.database.dao.MessageDao
import com.voicemessenger.database.dao.UserDao
import com.voicemessenger.database.entities.Chat
import com.voicemessenger.database.entities.User
import com.voicemessenger.model.Message

@Database(
    entities = [User::class, Chat::class, Message::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice_messenger_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}