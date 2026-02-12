package com.voicemessenger

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.voicemessenger.adapter.ContactsAdapter
import com.voicemessenger.database.AppDatabase
import com.voicemessenger.database.entities.Chat
import com.voicemessenger.databinding.ActivityMainBinding
import com.voicemessenger.model.Contact
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var database: AppDatabase
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        auth = Firebase.auth
        db = Firebase.firestore
        database = AppDatabase.getDatabase(this)
        
        // Проверяем авторизацию
        if (auth.currentUser == null) {
            startAuthActivity()
            return
        }
        
        setupRecyclerView()
        setupUI()
        loadChats()
    }
    
    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter { contact ->
            openChat(contact)
        }
        
        binding.recyclerViewContacts.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }
    
    private fun setupUI() {
        binding.fabAddContact.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }
        
        // Добавляем меню для выхода из аккаунта
        binding.fabAddContact.setOnLongClickListener {
            auth.signOut()
            startAuthActivity()
            true
        }
    }
    
    private fun loadChats() {
        lifecycleScope.launch {
            database.chatDao().getAllChats().collect { chats ->
                val contacts = chats.map { chat ->
                    Contact(
                        id = chat.participantId,
                        name = chat.participantName,
                        status = if (chat.isOnline) "Онлайн" else "Был в сети недавно",
                        avatarUrl = chat.participantAvatar,
                        isOnline = chat.isOnline
                    )
                }
                
                runOnUiThread {
                    if (contacts.isEmpty()) {
                        // Добавляем демо контакты если нет реальных чатов
                        addDemoContacts()
                    } else {
                        contactsAdapter.updateContacts(contacts)
                    }
                }
            }
        }
    }
    
    private fun addDemoContacts() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        lifecycleScope.launch {
            // Создаем демо чаты
            val demoChats = listOf(
                Chat(
                    chatId = "demo_chat_1",
                    participantId = "demo_user_1",
                    participantName = "Демо Пользователь 1",
                    lastMessage = "Привет! Это демо чат",
                    isOnline = true
                ),
                Chat(
                    chatId = "demo_chat_2",
                    participantId = "demo_user_2", 
                    participantName = "Демо Пользователь 2",
                    lastMessage = "Как дела?",
                    isOnline = false
                )
            )
            
            demoChats.forEach { chat ->
                database.chatDao().insertChat(chat)
            }
        }
    }
    
    private fun openChat(contact: Contact) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("CONTACT_ID", contact.id)
            putExtra("CONTACT_NAME", contact.name)
        }
        startActivity(intent)
    }
    
    private fun startAuthActivity() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
