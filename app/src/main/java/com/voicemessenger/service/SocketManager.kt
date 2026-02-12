package com.voicemessenger.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

class SocketManager private constructor() {
    
    private var socket: Socket? = null
    private var isConnected = false
    
    // Callbacks для различных событий
    private val connectionListeners = mutableListOf<(Boolean) -> Unit>()
    private val messageListeners = mutableListOf<(JSONObject) -> Unit>()
    private val callListeners = mutableListOf<(String, JSONObject) -> Unit>()
    private val userListeners = mutableListOf<(String, JSONObject) -> Unit>()
    
    companion object {
        private const val TAG = "SocketManager"
        private const val SERVER_URL = "http://10.0.2.2:3000" // Для эмулятора
        // private const val SERVER_URL = "http://192.168.1.XXX:3000" // Для реального устройства - замените на ваш IP
        
        @Volatile
        private var INSTANCE: SocketManager? = null
        
        fun getInstance(): SocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketManager().also { INSTANCE = it }
            }
        }
    }
    
    fun connect() {
        try {
            Log.d(TAG, "Connecting to server: $SERVER_URL")
            
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                timeout = 5000
            }
            
            socket = IO.socket(SERVER_URL, options)
            
            socket?.apply {
                // Основные события подключения
                on(Socket.EVENT_CONNECT, onConnect)
                on(Socket.EVENT_DISCONNECT, onDisconnect)
                on(Socket.EVENT_CONNECT_ERROR, onConnectError)
                
                // Пользовательские события
                on("users-list", onUsersList)
                on("user-online", onUserOnline) 
                on("user-offline", onUserOffline)
                
                // События чатов
                on("joined-room", onJoinedRoom)
                on("user-joined", onUserJoined)
                on("user-left", onUserLeft)
                on("new-message", onNewMessage)
                
                // События звонков
                on("incoming-call", onIncomingCall)
                on("call-accepted", onCallAccepted)
                on("call-rejected", onCallRejected)
                on("call-ended", onCallEnded)
                
                // WebRTC сигналинг
                on("offer", onOffer)
                on("answer", onAnswer)
                on("ice-candidate", onIceCandidate)
                
                // Обработка ошибок
                on("error", onError)
                
                connect()
            }
            
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Socket connection error", e)
        }
    }
    
    fun disconnect() {
        Log.d(TAG, "Disconnecting from server")
        socket?.disconnect()
        socket?.off()
        socket = null
        isConnected = false
        notifyConnectionListeners(false)
    }
    
    fun registerUser(userId: String, displayName: String, email: String) {
        if (!isConnected) {
            Log.w(TAG, "Socket not connected, cannot register user")
            return
        }
        
        val userData = JSONObject().apply {
            put("userId", userId)
            put("displayName", displayName)
            put("email", email)
        }
        
        Log.d(TAG, "Registering user: $displayName")
        socket?.emit("register", userData)
    }
    
    fun joinRoom(roomId: String, userId: String) {
        if (!isConnected) {
            Log.w(TAG, "Socket not connected, cannot join room")
            return
        }
        
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("userId", userId)
        }
        
        Log.d(TAG, "Joining room: $roomId")
        socket?.emit("join-room", data)
    }
    
    fun leaveRoom(roomId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
        }
        
        Log.d(TAG, "Leaving room: $roomId")
        socket?.emit("leave-room", data)
    }
    
    fun sendMessage(roomId: String, message: String, messageType: String = "text") {
        if (!isConnected) {
            Log.w(TAG, "Socket not connected, cannot send message")
            return
        }
        
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("message", message)
            put("messageType", messageType)
        }
        
        Log.d(TAG, "Sending message to room $roomId: ${message.take(50)}...")
        socket?.emit("send-message", data)
    }
    
    // WebRTC сигналинг методы
    fun sendOffer(targetSocketId: String, offer: String) {
        val data = JSONObject().apply {
            put("target", targetSocketId)
            put("offer", offer)
        }
        
        Log.d(TAG, "Sending WebRTC offer to: $targetSocketId")
        socket?.emit("offer", data)
    }
    
    fun sendAnswer(targetSocketId: String, answer: String) {
        val data = JSONObject().apply {
            put("target", targetSocketId)
            put("answer", answer)
        }
        
        Log.d(TAG, "Sending WebRTC answer to: $targetSocketId")
        socket?.emit("answer", data)
    }
    
    fun sendIceCandidate(targetSocketId: String, candidate: String) {
        val data = JSONObject().apply {
            put("target", targetSocketId)
            put("candidate", candidate)
        }
        
        Log.d(TAG, "Sending ICE candidate to: $targetSocketId")
        socket?.emit("ice-candidate", data)
    }
    
    // Звонки
    fun callUser(targetUserId: String, callType: String = "voice") {
        if (!isConnected) {
            Log.w(TAG, "Socket not connected, cannot make call")
            return
        }
        
        val data = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("callType", callType)
        }
        
        Log.d(TAG, "Calling user: $targetUserId")
        socket?.emit("call-user", data)
    }
    
    fun acceptCall(callId: String, targetSocketId: String) {
        val data = JSONObject().apply {
            put("callId", callId)
            put("targetSocketId", targetSocketId)
        }
        
        Log.d(TAG, "Accepting call: $callId")
        socket?.emit("accept-call", data)
    }
    
    fun rejectCall(callId: String, targetSocketId: String) {
        val data = JSONObject().apply {
            put("callId", callId)
            put("targetSocketId", targetSocketId)
        }
        
        Log.d(TAG, "Rejecting call: $callId")
        socket?.emit("reject-call", data)
    }
    
    fun endCall(callId: String, targetSocketId: String?) {
        val data = JSONObject().apply {
            put("callId", callId)
            if (targetSocketId != null) {
                put("targetSocketId", targetSocketId)
            }
        }
        
        Log.d(TAG, "Ending call: $callId")
        socket?.emit("end-call", data)
    }
    
    // Listeners registration
    fun addConnectionListener(listener: (Boolean) -> Unit) {
        connectionListeners.add(listener)
    }
    
    fun addMessageListener(listener: (JSONObject) -> Unit) {
        messageListeners.add(listener)
    }
    
    fun addCallListener(listener: (String, JSONObject) -> Unit) {
        callListeners.add(listener)
    }
    
    fun addUserListener(listener: (String, JSONObject) -> Unit) {
        userListeners.add(listener)
    }
    
    // Socket event handlers
    private val onConnect = Emitter.Listener {
        Log.d(TAG, "Connected to server")
        isConnected = true
        notifyConnectionListeners(true)
        
        // Автоматическая регистрация пользователя при подключении
        FirebaseAuth.getInstance().currentUser?.let { user ->
            registerUser(
                userId = user.uid,
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "User",
                email = user.email ?: ""
            )
        }
    }
    
    private val onDisconnect = Emitter.Listener {
        Log.d(TAG, "Disconnected from server")
        isConnected = false
        notifyConnectionListeners(false)
    }
    
    private val onConnectError = Emitter.Listener { args ->
        Log.e(TAG, "Connection error: ${args.joinToString()}")
        isConnected = false
        notifyConnectionListeners(false)
    }
    
    private val onError = Emitter.Listener { args ->
        Log.e(TAG, "Socket error: ${args.joinToString()}")
    }
    
    // User events
    private val onUsersList = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "Users list received")
            notifyUserListeners("users-list", data as JSONObject)
        }
    }
    
    private val onUserOnline = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "User came online")
            notifyUserListeners("user-online", data as JSONObject)
        }
    }
    
    private val onUserOffline = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "User went offline")
            notifyUserListeners("user-offline", data as JSONObject)
        }
    }
    
    // Room events
    private val onJoinedRoom = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "Joined room successfully")
            notifyUserListeners("joined-room", data as JSONObject)
        }
    }
    
    private val onUserJoined = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "User joined room")
            notifyUserListeners("user-joined", data as JSONObject)
        }
    }
    
    private val onUserLeft = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "User left room")
            notifyUserListeners("user-left", data as JSONObject)
        }
    }
    
    private val onNewMessage = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "New message received")
            notifyMessageListeners(data as JSONObject)
        }
    }
    
    // Call events
    private val onIncomingCall = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "Incoming call received")
            notifyCallListeners("incoming-call", data as JSONObject)
        }
    }
    
    private val onCallAccepted = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "Call accepted")
            notifyCallListeners("call-accepted", data as JSONObject)
        }
    }
    
    private val onCallRejected = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "Call rejected")
            notifyCallListeners("call-rejected", data as JSONObject)
        }
    }
    
    private val onCallEnded = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "Call ended")
            notifyCallListeners("call-ended", data as JSONObject)
        }
    }
    
    // WebRTC signaling events
    private val onOffer = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "WebRTC offer received")
            notifyCallListeners("webrtc-offer", data as JSONObject)
        }
    }
    
    private val onAnswer = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "WebRTC answer received")
            notifyCallListeners("webrtc-answer", data as JSONObject)
        }
    }
    
    private val onIceCandidate = Emitter.Listener { args ->
        args.firstOrNull()?.let { data ->
            Log.d(TAG, "ICE candidate received")
            notifyCallListeners("webrtc-ice-candidate", data as JSONObject)
        }
    }
    
    // Notification methods
    private fun notifyConnectionListeners(connected: Boolean) {
        connectionListeners.forEach { it(connected) }
    }
    
    private fun notifyMessageListeners(data: JSONObject) {
        messageListeners.forEach { it(data) }
    }
    
    private fun notifyCallListeners(event: String, data: JSONObject) {
        callListeners.forEach { it(event, data) }
    }
    
    private fun notifyUserListeners(event: String, data: JSONObject) {
        userListeners.forEach { it(event, data) }
    }
    
    fun isConnected(): Boolean = isConnected
    
    fun getSocketId(): String? = socket?.id()
}