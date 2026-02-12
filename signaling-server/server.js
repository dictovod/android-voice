const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

app.use(cors());
app.use(express.json());

// Хранилище активных пользователей и комнат
const users = new Map(); // socketId -> userInfo
const rooms = new Map(); // roomId -> roomInfo

// Middleware для CORS
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept');
    next();
});

// Основные эндпоинты
app.get('/', (req, res) => {
    res.json({
        message: 'Voice Messenger Signaling Server',
        version: '1.0.0',
        status: 'running',
        users: users.size,
        rooms: rooms.size
    });
});

app.get('/health', (req, res) => {
    res.json({ 
        status: 'healthy',
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// Socket.IO обработчики
io.on('connection', (socket) => {
    console.log(`[${new Date().toISOString()}] User connected: ${socket.id}`);
    
    // Регистрация пользователя
    socket.on('register', (userData) => {
        const userInfo = {
            socketId: socket.id,
            userId: userData.userId,
            displayName: userData.displayName,
            email: userData.email,
            isOnline: true,
            joinedAt: new Date().toISOString()
        };
        
        users.set(socket.id, userInfo);
        console.log(`[${new Date().toISOString()}] User registered: ${userData.displayName} (${userData.userId})`);
        
        // Уведомляем всех о новом пользователе
        socket.broadcast.emit('user-online', userInfo);
        
        // Отправляем список всех онлайн пользователей
        const onlineUsers = Array.from(users.values());
        socket.emit('users-list', onlineUsers);
    });
    
    // Присоединение к комнате
    socket.on('join-room', (data) => {
        const { roomId, userId } = data;
        const user = users.get(socket.id);
        
        if (!user) {
            socket.emit('error', { message: 'User not registered' });
            return;
        }
        
        socket.join(roomId);
        
        if (!rooms.has(roomId)) {
            rooms.set(roomId, {
                id: roomId,
                participants: [],
                createdAt: new Date().toISOString()
            });
        }
        
        const room = rooms.get(roomId);
        if (!room.participants.some(p => p.userId === userId)) {
            room.participants.push(user);
        }
        
        console.log(`[${new Date().toISOString()}] User ${user.displayName} joined room: ${roomId}`);
        
        // Уведомляем всех в комнате
        socket.to(roomId).emit('user-joined', {
            user: user,
            roomId: roomId
        });
        
        socket.emit('joined-room', {
            roomId: roomId,
            participants: room.participants
        });
    });
    
    // Покидание комнаты
    socket.on('leave-room', (data) => {
        const { roomId } = data;
        const user = users.get(socket.id);
        
        if (!user) return;
        
        socket.leave(roomId);
        
        if (rooms.has(roomId)) {
            const room = rooms.get(roomId);
            room.participants = room.participants.filter(p => p.socketId !== socket.id);
            
            if (room.participants.length === 0) {
                rooms.delete(roomId);
            }
        }
        
        console.log(`[${new Date().toISOString()}] User ${user.displayName} left room: ${roomId}`);
        
        socket.to(roomId).emit('user-left', {
            user: user,
            roomId: roomId
        });
    });
    
    // WebRTC сигналинг
    socket.on('offer', (data) => {
        console.log(`[${new Date().toISOString()}] Offer from ${socket.id} to ${data.target}`);
        socket.to(data.target).emit('offer', {
            offer: data.offer,
            from: socket.id
        });
    });
    
    socket.on('answer', (data) => {
        console.log(`[${new Date().toISOString()}] Answer from ${socket.id} to ${data.target}`);
        socket.to(data.target).emit('answer', {
            answer: data.answer,
            from: socket.id
        });
    });
    
    socket.on('ice-candidate', (data) => {
        console.log(`[${new Date().toISOString()}] ICE candidate from ${socket.id} to ${data.target}`);
        socket.to(data.target).emit('ice-candidate', {
            candidate: data.candidate,
            from: socket.id
        });
    });
    
    // Инициация звонка
    socket.on('call-user', (data) => {
        const { targetUserId, callType } = data;
        const caller = users.get(socket.id);
        
        if (!caller) {
            socket.emit('error', { message: 'Caller not registered' });
            return;
        }
        
        // Находим целевого пользователя
        const targetUser = Array.from(users.values()).find(u => u.userId === targetUserId);
        
        if (!targetUser) {
            socket.emit('error', { message: 'Target user not found' });
            return;
        }
        
        const callId = uuidv4();
        
        console.log(`[${new Date().toISOString()}] ${callType} call from ${caller.displayName} to ${targetUser.displayName}`);
        
        // Отправляем уведомление о входящем звонке
        socket.to(targetUser.socketId).emit('incoming-call', {
            callId: callId,
            caller: caller,
            callType: callType
        });
        
        // Подтверждаем инициатору
        socket.emit('call-initiated', {
            callId: callId,
            targetUser: targetUser
        });
    });
    
    // Принятие звонка
    socket.on('accept-call', (data) => {
        const { callId, targetSocketId } = data;
        const user = users.get(socket.id);
        
        socket.to(targetSocketId).emit('call-accepted', {
            callId: callId,
            acceptedBy: user
        });
        
        console.log(`[${new Date().toISOString()}] Call ${callId} accepted by ${user.displayName}`);
    });
    
    // Отклонение звонка
    socket.on('reject-call', (data) => {
        const { callId, targetSocketId } = data;
        const user = users.get(socket.id);
        
        socket.to(targetSocketId).emit('call-rejected', {
            callId: callId,
            rejectedBy: user
        });
        
        console.log(`[${new Date().toISOString()}] Call ${callId} rejected by ${user.displayName}`);
    });
    
    // Завершение звонка
    socket.on('end-call', (data) => {
        const { callId, targetSocketId } = data;
        const user = users.get(socket.id);
        
        if (targetSocketId) {
            socket.to(targetSocketId).emit('call-ended', {
                callId: callId,
                endedBy: user
            });
        }
        
        console.log(`[${new Date().toISOString()}] Call ${callId} ended by ${user ? user.displayName : 'unknown'}`);
    });
    
    // Отправка сообщения в комнату
    socket.on('send-message', (data) => {
        const { roomId, message, messageType } = data;
        const user = users.get(socket.id);
        
        if (!user) return;
        
        const messageData = {
            id: uuidv4(),
            roomId: roomId,
            senderId: user.userId,
            senderName: user.displayName,
            content: message,
            messageType: messageType || 'text',
            timestamp: new Date().toISOString()
        };
        
        console.log(`[${new Date().toISOString()}] Message in room ${roomId} from ${user.displayName}: ${message.substring(0, 50)}...`);
        
        // Отправляем сообщение всем в комнате
        io.to(roomId).emit('new-message', messageData);
    });
    
    // Обработка отключения
    socket.on('disconnect', () => {
        const user = users.get(socket.id);
        
        if (user) {
            console.log(`[${new Date().toISOString()}] User disconnected: ${user.displayName} (${user.userId})`);
            
            // Уведомляем всех об отключении
            socket.broadcast.emit('user-offline', user);
            
            // Удаляем пользователя из всех комнат
            rooms.forEach((room, roomId) => {
                room.participants = room.participants.filter(p => p.socketId !== socket.id);
                if (room.participants.length === 0) {
                    rooms.delete(roomId);
                } else {
                    socket.to(roomId).emit('user-left', {
                        user: user,
                        roomId: roomId
                    });
                }
            });
            
            users.delete(socket.id);
        } else {
            console.log(`[${new Date().toISOString()}] Anonymous user disconnected: ${socket.id}`);
        }
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`
🚀 Voice Messenger Signaling Server
📍 Server running on port ${PORT}
🌐 Health check: http://localhost:${PORT}/health
🔗 Socket.IO endpoint: ws://localhost:${PORT}
⏰ Started at: ${new Date().toISOString()}
    `);
});