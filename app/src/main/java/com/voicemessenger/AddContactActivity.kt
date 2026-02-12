package com.voicemessenger

import android.Manifest
import android.content.ContentResolver
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
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
import com.voicemessenger.database.entities.User
import com.voicemessenger.databinding.ActivityAddContactBinding
import com.voicemessenger.model.Contact
import kotlinx.coroutines.launch

class AddContactActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    
    private lateinit var binding: ActivityAddContactBinding
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var database: AppDatabase
    
    companion object {
        private const val TAG = "AddContactActivity"
        private const val RC_CONTACTS_PERMISSION = 124
        private val CONTACTS_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        auth = Firebase.auth
        db = Firebase.firestore
        database = AppDatabase.getDatabase(this)
        
        setupToolbar()
        setupUI()
        setupRecyclerView()
    }
    
    private fun setupToolbar() {
        binding.toolbarAddContact.title = "Добавить контакт"
        setSupportActionBar(binding.toolbarAddContact)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupUI() {
        binding.buttonSearch.setOnClickListener {
            val query = binding.editTextSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchUsers(query)
            } else {
                Toast.makeText(this, "Введите email или имя для поиска", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.editTextSearch.setOnLongClickListener {
            loadPhoneContacts()
            true
        }
    }
    
    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter { contact ->
            addContactToChat(contact)
        }
        
        binding.recyclerViewResults.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(this@AddContactActivity)
        }
    }
    
    private fun searchUsers(query: String) {
        binding.buttonSearch.isEnabled = false
        binding.buttonSearch.text = "Поиск..."
        
        // Поиск в Firestore
        db.collection("users")
            .whereGreaterThanOrEqualTo("email", query)
            .whereLessThanOrEqualTo("email", query + '\uf8ff')
            .get()
            .addOnSuccessListener { documents ->
                val users = mutableListOf<Contact>()
                val currentUserId = auth.currentUser?.uid
                
                for (document in documents) {
                    val user = document.toObject(User::class.java)
                    // Исключаем текущего пользователя из результатов
                    if (user.uid != currentUserId) {
                        users.add(
                            Contact(
                                id = user.uid,
                                name = user.displayName,
                                status = user.email,
                                avatarUrl = user.avatarUrl,
                                isOnline = user.isOnline
                            )
                        )
                    }
                }
                
                // Если не нашли по email, ищем по имени
                if (users.isEmpty()) {
                    searchByDisplayName(query)
                } else {
                    runOnUiThread {
                        contactsAdapter.updateContacts(users)
                        binding.buttonSearch.isEnabled = true
                        binding.buttonSearch.text = "Поиск"
                        
                        if (users.isEmpty()) {
                            Toast.makeText(this, "Пользователи не найдены", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Ошибка поиска", e)
                runOnUiThread {
                    binding.buttonSearch.isEnabled = true
                    binding.buttonSearch.text = "Поиск"
                    Toast.makeText(this, "Ошибка поиска", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun searchByDisplayName(query: String) {
        db.collection("users")
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", query + '\uf8ff')
            .get()
            .addOnSuccessListener { documents ->
                val users = mutableListOf<Contact>()
                val currentUserId = auth.currentUser?.uid
                
                for (document in documents) {
                    val user = document.toObject(User::class.java)
                    if (user.uid != currentUserId) {
                        users.add(
                            Contact(
                                id = user.uid,
                                name = user.displayName,
                                status = user.email,
                                avatarUrl = user.avatarUrl,
                                isOnline = user.isOnline
                            )
                        )
                    }
                }
                
                runOnUiThread {
                    contactsAdapter.updateContacts(users)
                    binding.buttonSearch.isEnabled = true
                    binding.buttonSearch.text = "Поиск"
                    
                    if (users.isEmpty()) {
                        Toast.makeText(this, "Пользователи не найдены", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Ошибка поиска по имени", e)
                runOnUiThread {
                    binding.buttonSearch.isEnabled = true
                    binding.buttonSearch.text = "Поиск"
                    Toast.makeText(this, "Ошибка поиска", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun addContactToChat(contact: Contact) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        lifecycleScope.launch {
            // Проверяем, есть ли уже чат с этим пользователем
            val existingChat = database.chatDao().getChatByParticipant(contact.id)
            if (existingChat != null) {
                runOnUiThread {
                    Toast.makeText(this@AddContactActivity, "Чат уже существует", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            
            // Создаем новый чат
            val chatId = "${currentUserId}_${contact.id}"
            val chat = Chat(
                chatId = chatId,
                participantId = contact.id,
                participantName = contact.name,
                participantAvatar = contact.avatarUrl,
                lastMessage = "Чат создан",
                isOnline = contact.isOnline
            )
            
            database.chatDao().insertChat(chat)
            
            runOnUiThread {
                Toast.makeText(this@AddContactActivity, "Контакт добавлен!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    @AfterPermissionGranted(RC_CONTACTS_PERMISSION)
    private fun loadPhoneContacts() {
        if (EasyPermissions.hasPermissions(this, *CONTACTS_PERMISSIONS)) {
            val phoneContacts = getPhoneContacts()
            if (phoneContacts.isNotEmpty()) {
                contactsAdapter.updateContacts(phoneContacts)
                Toast.makeText(this, "Загружено контактов: ${phoneContacts.size}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Контакты не найдены", Toast.LENGTH_SHORT).show()
            }
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Для загрузки контактов нужны разрешения",
                RC_CONTACTS_PERMISSION,
                *CONTACTS_PERMISSIONS
            )
        }
    }
    
    private fun getPhoneContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.DATA
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                )
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                )
                val email = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DATA)
                )
                
                if (name != null && email != null) {
                    contacts.add(
                        Contact(
                            id = "phone_$contactId",
                            name = name,
                            status = email,
                            avatarUrl = null,
                            isOnline = false
                        )
                    )
                }
            }
        }
        
        return contacts.distinctBy { it.status } // Удаляем дубликаты по email
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
        Log.d(TAG, "Permissions granted: $perms")
    }
    
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, "Разрешения необходимы для загрузки контактов", Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}