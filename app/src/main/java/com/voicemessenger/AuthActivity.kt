package com.voicemessenger

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.voicemessenger.database.AppDatabase
import com.voicemessenger.database.entities.User
import com.voicemessenger.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var database: AppDatabase
    
    private var isLoginMode = true
    
    companion object {
        private const val TAG = "AuthActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        auth = Firebase.auth
        db = Firebase.firestore
        database = AppDatabase.getDatabase(this)
        
        setupUI()
        
        // Проверяем, авторизован ли пользователь
        if (auth.currentUser != null) {
            startMainActivity()
            return
        }
    }
    
    private fun setupUI() {
        binding.buttonAuth.text = if (isLoginMode) "Войти" else "Зарегистрироваться"
        binding.buttonSwitchMode.text = if (isLoginMode) "Нет аккаунта? Регистрация" else "Есть аккаунт? Войти"
        
        binding.buttonAuth.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            val displayName = binding.editTextDisplayName.text.toString().trim()
            
            if (validateInput(email, password, displayName)) {
                if (isLoginMode) {
                    signIn(email, password)
                } else {
                    signUp(email, password, displayName)
                }
            }
        }
        
        binding.buttonSwitchMode.setOnClickListener {
            switchMode()
        }
        
        // Демо кнопки для быстрого входа
        binding.buttonDemoUser1.setOnClickListener {
            signInWithDemo("demo1@example.com", "123456", "Демо Пользователь 1")
        }
        
        binding.buttonDemoUser2.setOnClickListener {
            signInWithDemo("demo2@example.com", "123456", "Демо Пользователь 2")
        }
    }
    
    private fun validateInput(email: String, password: String, displayName: String): Boolean {
        if (email.isEmpty()) {
            binding.editTextEmail.error = "Введите email"
            return false
        }
        
        if (password.length < 6) {
            binding.editTextPassword.error = "Пароль должен содержать минимум 6 символов"
            return false
        }
        
        if (!isLoginMode && displayName.isEmpty()) {
            binding.editTextDisplayName.error = "Введите имя"
            return false
        }
        
        return true
    }
    
    private fun signIn(email: String, password: String) {
        binding.buttonAuth.isEnabled = false
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.buttonAuth.isEnabled = true
                if (task.isSuccessful) {
                    Log.d(TAG, "Авторизация успешна")
                    startMainActivity()
                } else {
                    Log.w(TAG, "Ошибка авторизации", task.exception)
                    Toast.makeText(this, "Ошибка авторизации: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun signUp(email: String, password: String, displayName: String) {
        binding.buttonAuth.isEnabled = false
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        createUserProfile(it.uid, displayName, email)
                    }
                } else {
                    binding.buttonAuth.isEnabled = true
                    Log.w(TAG, "Ошибка регистрации", task.exception)
                    Toast.makeText(this, "Ошибка регистрации: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun signInWithDemo(email: String, password: String, displayName: String) {
        // Сначала пытаемся войти
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Демо пользователь вошел")
                    startMainActivity()
                } else {
                    // Если не получилось войти, создаем аккаунт
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { createTask ->
                            if (createTask.isSuccessful) {
                                val user = auth.currentUser
                                user?.let {
                                    createUserProfile(it.uid, displayName, email)
                                }
                            } else {
                                Toast.makeText(this, "Ошибка создания демо пользователя", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
    }
    
    private fun createUserProfile(uid: String, displayName: String, email: String) {
        val user = User(
            uid = uid,
            displayName = displayName,
            email = email,
            isOnline = true
        )
        
        // Сохраняем в Firestore
        db.collection("users").document(uid).set(user)
            .addOnSuccessListener {
                Log.d(TAG, "Профиль пользователя создан")
                // Сохраняем в локальную БД
                lifecycleScope.launch {
                    database.userDao().insertUser(user)
                    runOnUiThread {
                        binding.buttonAuth.isEnabled = true
                        startMainActivity()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Ошибка создания профиля", e)
                binding.buttonAuth.isEnabled = true
                Toast.makeText(this, "Ошибка создания профиля", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun switchMode() {
        isLoginMode = !isLoginMode
        binding.buttonAuth.text = if (isLoginMode) "Войти" else "Зарегистрироваться"
        binding.buttonSwitchMode.text = if (isLoginMode) "Нет аккаунта? Регистрация" else "Есть аккаунт? Войти"
        
        // Показываем/скрываем поле имени
        binding.textInputLayoutDisplayName.visibility = if (isLoginMode) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }
    
    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}