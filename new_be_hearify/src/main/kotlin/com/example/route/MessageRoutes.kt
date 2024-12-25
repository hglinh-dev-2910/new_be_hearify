package com.example.route

import com.example.database.MessagesSchema
import com.example.database.UsersSchema
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
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

//connection manager
val connections = ConcurrentHashMap<Int, DefaultWebSocketSession>() //map userId va webSocket
val mutex = Mutex() //dong bo hoa connections

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


fun Route.messageRoutes() {
    route("/messages") {
        webSocket("/connect") {
            val userId = call.parameters["userId"]?.toIntOrNull()
            val isConnected = validateConnection(userId!!)

            if (!isConnected) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid user ID"))
                return@webSocket
            }

            mutex.withLock {
                connections[userId] = this
            }
            println("User $userId connected")

            // Khi user kết nối, gửi tất cả tin nhắn "pending" cho họ
            val pendingMessages = fetchPendingMessages(userId)

            val sentMessageIds = mutableListOf<Int>()
            for (message in pendingMessages) {
                try {
                    send(Frame.Text(Json.encodeToString(message)))
                    sentMessageIds.add(message.id) // If sent successfully, track the IDs
                } catch (e: Exception) {
                    println("Failed to send message ${message.id} to user $userId: ${e.message}")
                }
            }

            // Mark sent messages as "sent"
            if (sentMessageIds.isNotEmpty()) {
                markMessagesAsSent(sentMessageIds)
            }



            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        println("Received from $userId: $receivedText")

                        // Handle Read Action
                        val decodedPayload = Json.decodeFromString<Map<String, Any>>(receivedText)
                        if (decodedPayload["action"] == "read") {
                            val readPayload = Json.decodeFromString<ReadMessage>(receivedText)
                            markMessagesAsRead(readPayload.messageIds)
                            println("Messages marked as read: ${readPayload.messageIds}")
                            continue
                        }
                        // Handle New Chat Message
                        val chatMessage = Json.decodeFromString<ChatMessage>(receivedText)

                        // Validate Users
                        if (!validateUsers(chatMessage.senderId, chatMessage.receiverId)) {
                            send(Frame.Text("""{"status": "error", "message": "Sender or receiver does not exist"}"""))
                            continue
                        }
                        // Store Message in Database
                        val messageId = storeMessage(chatMessage)

                        // Forward message if the receiver is online
                        val receiverSession = connections[chatMessage.receiverId]
                        if (receiverSession != null) {
                            receiverSession.send(Frame.Text(receivedText))
                            markMessagesAsSent(listOf(messageId)) // If sent successfully, mark as sent
                        } else {
                            println("Receiver ${chatMessage.receiverId} is offline. Message stored as pending.")
                        }

                        // Acknowledge to the sender
                        send(Frame.Text("""{"status": "sent", "receiverId": ${chatMessage.receiverId}}"""))


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
    }

    // API lấy lịch sử tin nhắn giữa 2 người dùng
    get("/history") {
        val senderId = call.request.queryParameters["senderId"]?.toIntOrNull()
        val receiverId = call.request.queryParameters["receiverId"]?.toIntOrNull()

        if (senderId == null || receiverId == null) {
            call.respondText("Invalid senderId or receiverId", status = HttpStatusCode.BadRequest)
            return@get
        }

        val history = fetchChatHistory(senderId, receiverId)
        call.respond(history)
    }
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


