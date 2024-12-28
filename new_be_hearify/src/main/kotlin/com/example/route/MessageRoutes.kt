package com.example.route

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.database.MessagesSchema
import com.example.database.UsersSchema
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

// Connection manager - sử dụng WebSocketServerSession để linh hoạt hơn
val connections = ConcurrentHashMap<Int, WebSocketServerSession>()
val mutex = Mutex()

@Serializable
data class ChatMessage(
    val id: Int = 0,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val isRead: Boolean = false,
    val status: String = "pending" //default
)

@Serializable
data class ReadMessage(
    val action: String,
    val messageIds: List<Int>
)

fun Application.messageRoutes() {
    install(Authentication) {
        jwt {
            // Cấu hình JWT authentication
            val jwtAudience = "jwt-audience"
            val jwtIssuer = "https://jwt-provider-domain/"
            realm = "Access to 'messages'"
            verifier(
                JWT
                    .require(Algorithm.HMAC256("secret"))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
            }
        }
    }

    routing {
        authenticate { // Yêu cầu authentication cho route /messages
            route("/messages") {
                webSocket("/connect") {
                    // Lấy userId từ principal
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asInt()

                    handleWebSocketConnection(userId, this) // Tách logic xử lý kết nối WebSocket ra hàm riêng
                }

                get("/history") {
                    getChatHistory(call) // Tách logic xử lý API lấy lịch sử tin nhắn ra hàm riêng
                }

                get("/conversations") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asInt()

                    val conversations = getConversations(userId)
                    call.respond(conversations)
                }
            }
        }
    }
}

// Hàm xử lý kết nối WebSocket
private suspend fun handleWebSocketConnection(userId: Int, webSocketSession: WebSocketServerSession) {
    val isConnected = validateConnection(userId)

    if (!isConnected) {
        webSocketSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid user ID"))
        return
    }

    mutex.withLock {
        connections[userId] = webSocketSession // Không cần ép kiểu nữa
    }
    println("User $userId connected")

    // Gửi tin nhắn pending cho user
    sendPendingMessages(userId, webSocketSession) // Tách logic gửi tin nhắn pending ra hàm riêng

    try {
        for (frame in webSocketSession.incoming) {
            if (frame is Frame.Text) {
                handleIncomingMessage(userId, frame, webSocketSession) // Tách logic xử lý tin nhắn đến ra hàm riêng
            }
        }
    } catch (e: ClosedReceiveChannelException) {
        println("User $userId disconnected with error: ${e.message}")
    } finally {
        mutex.withLock {
            connections.remove(userId)
        }
        println("User $userId disconnected")
    }
}

// Hàm gửi tin nhắn pending cho user
private suspend fun sendPendingMessages(userId: Int, webSocketSession: WebSocketServerSession) {
    val pendingMessages = fetchPendingMessages(userId)

    val sentMessageIds = mutableListOf<Int>()
    for (message in pendingMessages) {
        try {
            webSocketSession.send(Frame.Text(Json.encodeToString(message)))
            sentMessageIds.add(message.id)
        } catch (e: Exception) {
            println("Failed to send message ${message.id} to user $userId: ${e.message}")
        }
    }

    if (sentMessageIds.isNotEmpty()) {
        markMessagesAsSent(sentMessageIds)
    }
}

// Hàm xử lý tin nhắn đến
private suspend fun handleIncomingMessage(userId: Int, frame: Frame.Text, webSocketSession: WebSocketServerSession) {
    val receivedText = frame.readText()
    println("Received from $userId: $receivedText")

    // Handle Read Action
    val decodedPayload = Json.decodeFromString<Map<String, Any>>(receivedText)
    if (decodedPayload["action"] == "read") {
        val readPayload = Json.decodeFromString<ReadMessage>(receivedText)
        markMessagesAsRead(readPayload.messageIds)
        println("Messages marked as read: ${readPayload.messageIds}")
        return
    }

    // Handle New Chat Message
    val chatMessage = Json.decodeFromString<ChatMessage>(receivedText)

    // Validate Users
    if (!validateUsers(chatMessage.senderId, chatMessage.receiverId)) {
        webSocketSession.send(Frame.Text("""{"status": "error", "message": "Sender or receiver does not exist"}"""))
        return
    }

    // Store Message in Database
    val messageId = storeMessage(chatMessage)

    // Forward message if the receiver is online
    val receiverSession = connections[chatMessage.receiverId]
    if (receiverSession != null && receiverSession.isActive) { // Kiểm tra isActive trước khi gửi tin nhắn
        receiverSession.send(Frame.Text(receivedText))
        markMessagesAsSent(listOf(messageId))
    } else {
        println("Receiver ${chatMessage.receiverId} is offline. Message stored as pending.")
    }

    // Acknowledge to the sender
    webSocketSession.send(Frame.Text("""{"status": "sent", "receiverId": ${chatMessage.receiverId}}"""))
}

// Hàm xử lý API lấy lịch sử tin nhắn
private suspend fun getChatHistory(call: ApplicationCall) {
    val senderId = call.request.queryParameters["senderId"]?.toIntOrNull()
    val receiverId = call.request.queryParameters["receiverId"]?.toIntOrNull()

    if (senderId == null || receiverId == null) {
        call.respondText("Invalid senderId or receiverId", status = HttpStatusCode.BadRequest)
        return
    }

    val history = fetchChatHistory(senderId, receiverId)
    call.respond(history)
}

// Lưu tin nhắn vào cơ sở dữ liệu
fun storeMessage(chatMessage: ChatMessage): Int {
    return transaction {
        MessagesSchema.insert {
            it[senderId] = chatMessage.senderId
            it[receiverId] = chatMessage.receiverId
            it[content] = chatMessage.content
            it[timestamp] = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(chatMessage.timestamp),
                ZoneId.systemDefault()
            )
            it[isRead] = false
            it[status] = "pending"
        }

        // Truy vấn lại để lấy ID vừa được thêm vào
        MessagesSchema.selectAll().where { MessagesSchema.id neq 0 }
            .orderBy(MessagesSchema.id, SortOrder.DESC)
            .limit(1)
            .map { it[MessagesSchema.id] }
            .first()
    }
}

// Truy vấn lịch sử tin nhắn giữa 2 user từ database
fun fetchChatHistory(senderId: Int, receiverId: Int): List<ChatMessage> {
    return transaction {
        MessagesSchema.selectAll().where {
            ((MessagesSchema.senderId eq senderId) and (MessagesSchema.receiverId eq receiverId)) or
                    ((MessagesSchema.senderId eq receiverId) and (MessagesSchema.receiverId eq senderId))
        }.orderBy(MessagesSchema.timestamp, SortOrder.ASC)
            .map {
                ChatMessage(
                    senderId = it[MessagesSchema.senderId],
                    receiverId = it[MessagesSchema.receiverId],
                    content = it[MessagesSchema.content],
                    timestamp = it[MessagesSchema.timestamp].atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    isRead = it[MessagesSchema.isRead],
                    status = it[MessagesSchema.status]
                )
            }
    }
}

// Truy vấn danh sách tin nhắn "chưa đọc" (pending) của 1 user
fun fetchPendingMessages(userId: Int): List<ChatMessage> {
    return transaction {
        MessagesSchema.selectAll().where { (MessagesSchema.receiverId eq userId) and (MessagesSchema.isRead eq false) }
            .orderBy(MessagesSchema.timestamp, SortOrder.ASC)
            .map {
                ChatMessage(
                    senderId = it[MessagesSchema.senderId],
                    receiverId = it[MessagesSchema.receiverId],
                    content = it[MessagesSchema.content],
                    timestamp = it[MessagesSchema.timestamp].atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    isRead = it[MessagesSchema.isRead],
                    status = it[MessagesSchema.status]
                )
            }
    }
}

// Đánh dấu các tin nhắn là "đã đọc"
fun markMessagesAsRead(messageIds: List<Int>) {
    transaction {
        MessagesSchema.update({ MessagesSchema.id inList messageIds }) {
            it[isRead] = true
            it[status] = "read"
        }
    }
    println("Marked messages as read: $messageIds")
}

fun markMessagesAsSent(messageIds: List<Int>) {
    transaction {
        MessagesSchema.update({ MessagesSchema.id inList messageIds }) {
            it[status] = "sent"
        }
    }
    println("Marked messages as sent: $messageIds")
}

// Hàm kiểm tra `senderId` và `receiverId` có hợp lệ hay không
fun validateUsers(senderId: Int, receiverId: Int): Boolean {
    return transaction {
        // Kiểm tra cả hai user trong bảng UsersSchema
        val senderExists = UsersSchema.selectAll().where { UsersSchema.id eq senderId }.count() > 0
        val receiverExists = UsersSchema.selectAll().where { UsersSchema.id eq receiverId }.count() > 0

        senderExists && receiverExists
    }
}

fun validateConnection(userId: Int): Boolean {
    return transaction {
        UsersSchema.selectAll().where { UsersSchema.id eq userId }.count() > 0
    }
}

// Data class Conversation
data class Conversation(
    val receiverId: Int,
    val lastMessage: String,
    val timestamp: Long
)

// Hàm lấy danh sách cuộc trò chuyện
private fun getConversations(userId: Int): List<Conversation> {
    return transaction {
        // Tìm tất cả các tin nhắn mà userId là người gửi hoặc người nhận
        val messages = MessagesSchema.selectAll()
            .where { (MessagesSchema.senderId eq userId) or (MessagesSchema.receiverId eq userId) }
            .orderBy(MessagesSchema.timestamp, SortOrder.DESC)

        // Group by người nhận/người gửi (người còn lại trong cuộc trò chuyện)
        val conversations = messages.groupBy {
            if (it[MessagesSchema.senderId] == userId) it[MessagesSchema.receiverId] else it[MessagesSchema.senderId]
        }

        // Lấy tin nhắn cuối cùng và thông tin cần thiết cho mỗi cuộc trò chuyện
        conversations.map { (receiverId, messages) ->
            val lastMessage = messages.first()
            Conversation(
                receiverId = receiverId,
                lastMessage = lastMessage[MessagesSchema.content],
                timestamp = lastMessage[MessagesSchema.timestamp].atZone(ZoneId.systemDefault()).toInstant()
                    .toEpochMilli()
            )
        }
    }
}