package com.voicemessenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicemessenger.R

class VoiceCallService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "VoiceCallChannel"
        private const val NOTIFICATION_ID = 1
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val contactName = intent?.getStringExtra("CONTACT_NAME") ?: "Unknown"
        startForeground(NOTIFICATION_ID, createNotification(contactName))
        
        // Voice call logic will be handled here
        
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Call",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Voice call notifications"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contactName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Call")
            .setContentText("Calling $contactName...")
            .setSmallIcon(R.drawable.ic_call)
            .setOngoing(true)
            .build()
    }
}