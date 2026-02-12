package com.voicemessenger

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voicemessenger.adapter.MessagesAdapter
import com.voicemessenger.databinding.ActivityChatBinding
import com.voicemessenger.model.Message
import com.voicemessenger.service.VoiceCallManager
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    
    private lateinit var binding: ActivityChatBinding
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var voiceCallManager: VoiceCallManager
    
    private var contactId: String = ""
    private var contactName: String = ""
    
    companion object {
        private const val RC_AUDIO_PERMISSION = 123
        private val AUDIO_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        contactId = intent.getStringExtra("CONTACT_ID") ?: ""
        contactName = intent.getStringExtra("CONTACT_NAME") ?: ""
        
        setupToolbar()
        setupRecyclerView()
        setupVoiceCall()
        setupUI()
    }
    
    private fun setupToolbar() {
        binding.toolbarChat.title = contactName
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter()
        binding.recyclerViewMessages.apply {
            adapter = messagesAdapter
            layoutManager = LinearLayoutManager(this@ChatActivity)
        }
        
        loadMessages()
    }
    
    private fun loadMessages() {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatId = "${currentUserId}_${contactId}"
        
        lifecycleScope.launch {
            val database = com.voicemessenger.database.AppDatabase.getDatabase(this@ChatActivity)
            database.messageDao().getMessagesByChatId(chatId).collect { messages ->
                runOnUiThread {
                    if (messages.isEmpty()) {
                        // Добавляем приветственное сообщение
                        val welcomeMessage = com.voicemessenger.model.Message(
                            id = "welcome_${System.currentTimeMillis()}",
                            chatId = chatId,
                            senderId = "system",
                            content = "Начните общение с $contactName! 😊",
                            timestamp = System.currentTimeMillis(),
                            isFromMe = false
                        )
                        messagesAdapter.updateMessages(listOf(welcomeMessage))
                    } else {
                        messagesAdapter.updateMessages(messages)
                        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }
    
    private fun setupUI() {
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
        
        binding.buttonVoiceCall.setOnClickListener {
            startVoiceCall()
        }
        
        binding.buttonEndCall.setOnClickListener {
            endVoiceCall()
        }
        
        binding.buttonRecordVoice.setOnClickListener {
            recordVoiceMessage()
        }
        
        updateCallButtons()
    }
    
    private fun setupVoiceCall() {
        voiceCallManager = VoiceCallManager(this)
    }
    
    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty()) {
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
            val chatId = "${currentUserId}_${contactId}"
            
            val message = Message(
                id = System.currentTimeMillis().toString(),
                chatId = chatId,
                senderId = currentUserId,
                content = messageText,
                timestamp = System.currentTimeMillis(),
                isFromMe = true
            )
            
            // Сохраняем в локальную БД
            lifecycleScope.launch {
                val database = com.voicemessenger.database.AppDatabase.getDatabase(this@ChatActivity)
                database.messageDao().insertMessage(message)
                
                // Обновляем последнее сообщение в чате
                database.chatDao().updateLastMessage(chatId, messageText, System.currentTimeMillis())
                
                runOnUiThread {
                    binding.editTextMessage.setText("")
                    binding.recyclerViewMessages.scrollToPosition(messagesAdapter.itemCount - 1)
                }
            }
        }
    }
    
    @AfterPermissionGranted(RC_AUDIO_PERMISSION)
    private fun startVoiceCall() {
        if (EasyPermissions.hasPermissions(this, *AUDIO_PERMISSIONS)) {
            voiceCallManager.startCall(contactId)
            updateCallButtons()
            Toast.makeText(this, "Начинаю голосовой звонок...", Toast.LENGTH_SHORT).show()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Для голосовых звонков нужны разрешения на запись аудио",
                RC_AUDIO_PERMISSION,
                *AUDIO_PERMISSIONS
            )
        }
    }
    
    private fun endVoiceCall() {
        voiceCallManager.endCall()
        updateCallButtons()
    }
    
    private fun updateCallButtons() {
        val isCallActive = voiceCallManager.isCallInProgress()
        binding.buttonVoiceCall.visibility = if (isCallActive) android.view.View.GONE else android.view.View.VISIBLE
        binding.buttonEndCall.visibility = if (isCallActive) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    @AfterPermissionGranted(RC_AUDIO_PERMISSION)
    private fun recordVoiceMessage() {
        if (EasyPermissions.hasPermissions(this, *AUDIO_PERMISSIONS)) {
            // Имитация записи голосового сообщения
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
            val chatId = "${currentUserId}_${contactId}"
            val voiceMessage = Message(
                id = System.currentTimeMillis().toString(),
                chatId = chatId,
                senderId = currentUserId,
                content = "🎤 Голосовое сообщение (3 сек)",
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                messageType = com.voicemessenger.model.MessageType.VOICE
            )
            messagesAdapter.addMessage(voiceMessage)
            binding.recyclerViewMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            Toast.makeText(this, "Голосовое сообщение записано (демо)", Toast.LENGTH_SHORT).show()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Для записи голосовых сообщений нужны разрешения на запись аудио",
                RC_AUDIO_PERMISSION,
                *AUDIO_PERMISSIONS
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
    
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        // Permissions granted
    }
    
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, "Разрешения необходимы для работы голосовых функций", Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceCallManager.cleanup()
    }
}
