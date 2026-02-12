package com.voicemessenger.service

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.json.JSONObject

class VoiceCallManager(private val context: Context) {
    
    private var webRTCManager: WebRTCManager? = null
    private var socketManager: SocketManager? = null
    private var currentCallId: String? = null
    private var isCallActive = false
    private var callStartTime = 0L
    
    // Callbacks
    private var onIncomingCall: ((String, String) -> Unit)? = null
    private var onCallStateChanged: ((String) -> Unit)? = null
    
    companion object {
        private const val TAG = "VoiceCallManager"
    }
    
    fun initialize() {
        Log.d(TAG, "Initializing VoiceCallManager")
        
        // Инициализируем WebRTC менеджер
        webRTCManager = WebRTCManager(context).apply {
            initialize()
            
            // Настраиваем callbacks
            setOnCallEstablished {
                Log.d(TAG, "Call established")
                onCallStateChanged?.invoke("connected")
                showToast("Звонок соединен")
            }
            
            setOnCallEnded {
                Log.d(TAG, "Call ended")
                isCallActive = false
                currentCallId = null
                onCallStateChanged?.invoke("ended")
                showToast("Звонок завершен")
                stopCallService()
            }
            
            setOnCallFailed { reason ->
                Log.e(TAG, "Call failed: $reason")
                isCallActive = false
                currentCallId = null
                onCallStateChanged?.invoke("failed")
                showToast("Ошибка звонка: $reason")
                stopCallService()
            }
            
            setOnRemoteStreamAdded {
                Log.d(TAG, "Remote stream added")
                showToast("Собеседник подключился")
            }
        }
        
        // Инициализируем Socket.IO менеджер
        socketManager = SocketManager.getInstance().apply {
            
            // Добавляем слушатель для входящих звонков
            addCallListener { event, data ->
                when (event) {
                    "incoming-call" -> {
                        val callId = data.getString("callId")
                        val callerName = data.getJSONObject("caller").getString("displayName")
                        Log.d(TAG, "Incoming call from: $callerName")
                        
                        onIncomingCall?.invoke(callId, callerName)
                        showToast("Входящий звонок от $callerName")
                    }
                    
                    "call-accepted" -> {
                        val callId = data.getString("callId")
                        Log.d(TAG, "Call accepted: $callId")
                        
                        // Инициируем WebRTC соединение
                        webRTCManager?.startCall(
                            data.getJSONObject("acceptedBy").getString("socketId"),
                            callId
                        )
                    }
                    
                    "call-rejected" -> {
                        val callId = data.getString("callId")
                        Log.d(TAG, "Call rejected: $callId")
                        
                        isCallActive = false
                        currentCallId = null
                        onCallStateChanged?.invoke("rejected")
                        showToast("Звонок отклонен")
                        stopCallService()
                    }
                    
                    "call-ended" -> {
                        val callId = data.getString("callId")
                        Log.d(TAG, "Call ended remotely: $callId")
                        
                        webRTCManager?.endCall()
                    }
                    
                    "webrtc-offer" -> {
                        val offer = data.getString("offer")
                        val fromSocketId = data.getString("from")
                        Log.d(TAG, "WebRTC offer received from: $fromSocketId")
                        
                        webRTCManager?.handleOffer(offer, fromSocketId)
                    }
                    
                    "webrtc-answer" -> {
                        val answer = data.getString("answer")
                        val fromSocketId = data.getString("from")
                        Log.d(TAG, "WebRTC answer received from: $fromSocketId")
                        
                        webRTCManager?.handleAnswer(answer, fromSocketId)
                    }
                    
                    "webrtc-ice-candidate" -> {
                        val candidate = data.getString("candidate")
                        val fromSocketId = data.getString("from")
                        Log.d(TAG, "WebRTC ICE candidate received from: $fromSocketId")
                        
                        webRTCManager?.handleIceCandidate(candidate, fromSocketId)
                    }
                }
            }
        }
        
        Log.d(TAG, "VoiceCallManager initialized successfully")
    }
    
    fun startCall(contactId: String) {
        Log.d(TAG, "Starting call to contact: $contactId")
        
        if (isCallActive) {
            Log.w(TAG, "Call already active")
            showToast("Звонок уже активен")
            return
        }
        
        if (socketManager?.isConnected() != true) {
            Log.e(TAG, "Socket not connected")
            showToast("Нет подключения к серверу")
            return
        }
        
        try {
            isCallActive = true
            callStartTime = System.currentTimeMillis()
            
            // Запускаем foreground service
            startCallService("Исходящий звонок")
            
            // Инициируем звонок через Socket.IO
            socketManager?.callUser(contactId, "voice")
            
            onCallStateChanged?.invoke("calling")
            showToast("Звоним...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call", e)
            isCallActive = false
            showToast("Ошибка при инициации звонка")
            stopCallService()
        }
    }
    
    fun acceptCall(callId: String, fromSocketId: String) {
        Log.d(TAG, "Accepting call: $callId from: $fromSocketId")
        
        try {
            currentCallId = callId
            isCallActive = true
            callStartTime = System.currentTimeMillis()
            
            // Запускаем foreground service
            startCallService("Входящий звонок")
            
            // Принимаем звонок через Socket.IO
            socketManager?.acceptCall(callId, fromSocketId)
            
            // Подготавливаем WebRTC
            webRTCManager?.acceptCall(fromSocketId, callId)
            
            onCallStateChanged?.invoke("accepted")
            showToast("Звонок принят")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call", e)
            isCallActive = false
            showToast("Ошибка при принятии звонка")
            stopCallService()
        }
    }
    
    fun rejectCall(callId: String, fromSocketId: String) {
        Log.d(TAG, "Rejecting call: $callId from: $fromSocketId")
        
        socketManager?.rejectCall(callId, fromSocketId)
        showToast("Звонок отклонен")
    }
    
    fun endCall() {
        Log.d(TAG, "Ending call")
        
        if (!isCallActive) {
            Log.d(TAG, "Call already inactive")
            return
        }
        
        try {
            val callDuration = if (callStartTime > 0) {
                (System.currentTimeMillis() - callStartTime) / 1000
            } else 0
            
            // Уведомляем сервер о завершении звонка
            currentCallId?.let { callId ->
                socketManager?.endCall(callId, null)
            }
            
            // Завершаем WebRTC соединение
            webRTCManager?.endCall()
            
            // Обнуляем состояние
            isCallActive = false
            currentCallId = null
            callStartTime = 0L
            
            // Останавливаем сервис
            stopCallService()
            
            onCallStateChanged?.invoke("ended")
            
            if (callDuration > 0) {
                showToast("Звонок завершен (${callDuration}с)")
            } else {
                showToast("Звонок завершен")
            }
            
            Log.d(TAG, "Call ended successfully. Duration: ${callDuration}s")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            isCallActive = false
            currentCallId = null
            showToast("Ошибка завершения звонка")
        }
    }
    
    // Управление звуком
    fun toggleMute(): Boolean {
        return webRTCManager?.toggleMute() ?: false
    }
    
    fun toggleSpeaker(): Boolean {
        return webRTCManager?.toggleSpeaker() ?: false
    }
    
    // Вспомогательные методы
    private fun startCallService(contactName: String) {
        try {
            val serviceIntent = Intent(context, VoiceCallService::class.java)
            serviceIntent.putExtra("CONTACT_NAME", contactName)
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "Call service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call service", e)
        }
    }
    
    private fun stopCallService() {
        try {
            val serviceIntent = Intent(context, VoiceCallService::class.java)
            context.stopService(serviceIntent)
            Log.d(TAG, "Call service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop call service", e)
        }
    }
    
    private fun showToast(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast", e)
        }
    }
    
    // Геттеры состояния
    fun isCallInProgress(): Boolean = isCallActive
    
    fun getCurrentCallId(): String? = currentCallId
    
    fun getCallDuration(): Long {
        return if (isCallActive && callStartTime > 0) {
            (System.currentTimeMillis() - callStartTime) / 1000
        } else {
            0
        }
    }
    
    // Callback setters
    fun setOnIncomingCall(callback: (String, String) -> Unit) {
        onIncomingCall = callback
    }
    
    fun setOnCallStateChanged(callback: (String) -> Unit) {
        onCallStateChanged = callback
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up VoiceCallManager")
        
        try {
            endCall()
            webRTCManager?.cleanup()
            webRTCManager = null
            socketManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
