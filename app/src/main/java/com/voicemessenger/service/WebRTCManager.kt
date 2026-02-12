package com.voicemessenger.service

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import org.json.JSONObject

class WebRTCManager(private val context: Context) {
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var isCallActive = false
    private var isInitiator = false
    private var remoteSocketId: String? = null
    private var callId: String? = null
    
    // Callbacks
    private var onCallEstablished: (() -> Unit)? = null
    private var onCallEnded: (() -> Unit)? = null
    private var onCallFailed: ((String) -> Unit)? = null
    private var onRemoteStreamAdded: (() -> Unit)? = null
    
    companion object {
        private const val TAG = "WebRTCManager"
        
        // STUN/TURN серверы для WebRTC
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
    }
    
    fun initialize() {
        try {
            Log.d(TAG, "Initializing WebRTC")
            
            // Инициализируем PeerConnectionFactory
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            
            val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .createAudioDeviceModule()
            
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()
            
            Log.d(TAG, "WebRTC initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
        }
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up WebRTC resources")
        
        localAudioTrack?.dispose()
        localAudioTrack = null
        
        peerConnection?.close()
        peerConnection = null
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        isCallActive = false
        isInitiator = false
        remoteSocketId = null
        callId = null
    }
    
    fun startCall(targetSocketId: String, callId: String) {
        Log.d(TAG, "Starting call to: $targetSocketId")
        
        this.remoteSocketId = targetSocketId
        this.callId = callId
        this.isInitiator = true
        this.isCallActive = true
        
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory not initialized")
            onCallFailed?.invoke("WebRTC not initialized")
            return
        }
        
        try {
            setupPeerConnection()
            setupLocalAudio()
            createOffer()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call", e)
            onCallFailed?.invoke("Failed to start call: ${e.message}")
        }
    }
    
    fun acceptCall(fromSocketId: String, callId: String) {
        Log.d(TAG, "Accepting call from: $fromSocketId")
        
        this.remoteSocketId = fromSocketId
        this.callId = callId
        this.isInitiator = false
        this.isCallActive = true
        
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory not initialized")
            onCallFailed?.invoke("WebRTC not initialized")
            return
        }
        
        try {
            setupPeerConnection()
            setupLocalAudio()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call", e)
            onCallFailed?.invoke("Failed to accept call: ${e.message}")
        }
    }
    
    fun endCall() {
        Log.d(TAG, "Ending call")
        
        isCallActive = false
        
        // Останавливаем локальное аудио
        localAudioTrack?.setEnabled(false)
        
        // Закрываем peer connection
        peerConnection?.close()
        peerConnection = null
        
        onCallEnded?.invoke()
        
        // Очищаем состояние
        remoteSocketId = null
        callId = null
        isInitiator = false
    }
    
    fun handleOffer(offer: String, fromSocketId: String) {
        Log.d(TAG, "Handling WebRTC offer from: $fromSocketId")
        
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized")
            return
        }
        
        try {
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, offer)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully")
                    createAnswer()
                }
                override fun onCreateFailure(p0: String?) {
                    Log.e(TAG, "Failed to set remote description: $p0")
                }
                override fun onSetFailure(p0: String?) {
                    Log.e(TAG, "Failed to set remote description: $p0")
                }
            }, sessionDescription)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle offer", e)
        }
    }
    
    fun handleAnswer(answer: String, fromSocketId: String) {
        Log.d(TAG, "Handling WebRTC answer from: $fromSocketId")
        
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized")
            return
        }
        
        try {
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, answer)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote answer set successfully")
                }
                override fun onCreateFailure(p0: String?) {
                    Log.e(TAG, "Failed to set remote answer: $p0")
                }
                override fun onSetFailure(p0: String?) {
                    Log.e(TAG, "Failed to set remote answer: $p0")
                }
            }, sessionDescription)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle answer", e)
        }
    }
    
    fun handleIceCandidate(candidateJson: String, fromSocketId: String) {
        Log.d(TAG, "Handling ICE candidate from: $fromSocketId")
        
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized")
            return
        }
        
        try {
            val candidateData = JSONObject(candidateJson)
            val candidate = IceCandidate(
                candidateData.getString("sdpMid"),
                candidateData.getInt("sdpMLineIndex"),
                candidateData.getString("candidate")
            )
            
            peerConnection?.addIceCandidate(candidate)
            Log.d(TAG, "ICE candidate added successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle ICE candidate", e)
        }
    }
    
    private fun setupPeerConnection() {
        Log.d(TAG, "Setting up PeerConnection")
        
		val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
			bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
			rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
			tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
		}
        
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state changed: $state")
            }
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state changed: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        Log.i(TAG, "ICE connection established")
                        onCallEstablished?.invoke()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.w(TAG, "ICE connection failed or disconnected")
                        onCallFailed?.invoke("Connection lost")
                    }
                    else -> {}
                }
            }
            
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state changed: $state")
            }
            
            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d(TAG, "New ICE candidate generated")
                candidate?.let {
                    val candidateJson = JSONObject().apply {
                        put("candidate", it.sdp)
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                    }.toString()
                    
                    remoteSocketId?.let { socketId ->
                        SocketManager.getInstance().sendIceCandidate(socketId, candidateJson)
                    }
                }
            }
            
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream added")
                stream?.audioTracks?.firstOrNull()?.let { audioTrack ->
                    remoteAudioTrack = audioTrack
                    audioTrack.setEnabled(true)
                    onRemoteStreamAdded?.invoke()
                }
            }
            
            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream removed")
            }
            
            override fun onDataChannel(dataChannel: DataChannel?) {}
            
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }
            
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
    }
    
    private fun setupLocalAudio() {
        Log.d(TAG, "Setting up local audio")
        
        // Настраиваем аудио менеджер
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
        
        // Создаем локальный аудио трек
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
        
        // Создаем медиа стрим и добавляем аудио трек
        val mediaStream = peerConnectionFactory?.createLocalMediaStream("local_stream")
        localAudioTrack?.let { mediaStream?.addTrack(it) }
        
        // Добавляем стрим в peer connection
        mediaStream?.let { peerConnection?.addStream(it) }
        
        Log.d(TAG, "Local audio setup completed")
    }
    
    private fun createOffer() {
        Log.d(TAG, "Creating WebRTC offer")
        
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                Log.d(TAG, "Offer created successfully")
                sessionDescription?.let { offer ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set successfully")
                            remoteSocketId?.let { socketId ->
                                SocketManager.getInstance().sendOffer(socketId, offer.description)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {
                            Log.e(TAG, "Failed to set local description: $p0")
                        }
                        override fun onSetFailure(p0: String?) {
                            Log.e(TAG, "Failed to set local description: $p0")
                        }
                    }, offer)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
                onCallFailed?.invoke("Failed to create offer")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
        }, mediaConstraints)
    }
    
    private fun createAnswer() {
        Log.d(TAG, "Creating WebRTC answer")
        
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                Log.d(TAG, "Answer created successfully")
                sessionDescription?.let { answer ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set successfully")
                            remoteSocketId?.let { socketId ->
                                SocketManager.getInstance().sendAnswer(socketId, answer.description)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {
                            Log.e(TAG, "Failed to set local description: $p0")
                        }
                        override fun onSetFailure(p0: String?) {
                            Log.e(TAG, "Failed to set local description: $p0")
                        }
                    }, answer)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
                onCallFailed?.invoke("Failed to create answer")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
            }
        }, mediaConstraints)
    }
    
    fun toggleMute(): Boolean {
        localAudioTrack?.let { track ->
            val newState = !track.enabled()
            track.setEnabled(newState)
            Log.d(TAG, "Audio muted: ${!newState}")
            return !newState
        }
        return false
    }
    
    fun toggleSpeaker(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isSpeakerOn = !audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        Log.d(TAG, "Speaker enabled: $isSpeakerOn")
        return isSpeakerOn
    }
    
    // Callback setters
    fun setOnCallEstablished(callback: () -> Unit) {
        onCallEstablished = callback
    }
    
    fun setOnCallEnded(callback: () -> Unit) {
        onCallEnded = callback
    }
    
    fun setOnCallFailed(callback: (String) -> Unit) {
        onCallFailed = callback
    }
    
    fun setOnRemoteStreamAdded(callback: () -> Unit) {
        onRemoteStreamAdded = callback
    }
    
    fun isCallActive(): Boolean = isCallActive
    
    fun getCallId(): String? = callId
}
