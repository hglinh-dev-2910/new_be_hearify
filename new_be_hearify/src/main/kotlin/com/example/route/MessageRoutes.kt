package com.example.route

import com.example.database.MessagesSchema
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

val connections = mutableMapOf<Int, DefaultWebSocketSession>()

@Serializable
data class ChatMessage(val receiverId: Int, val content: String)

fun Route.messageRoutes() {
    route("/chat") {
        //real time messages
        webSocket("/connect") {
            val senderId = call.parameters["senderId"]?.toIntOrNull()
            if (senderId == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing senderId"))
                return@webSocket
            }

            connections[senderId] = this
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val messageText = frame.readText()
                        val chatMessage = try {
                            Json.decodeFromString(ChatMessage.serializer(), messageText)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continue
                        }

                        val receiverId = chatMessage.receiverId
                        val content = chatMessage.content

                        //off thi luu mess vo db
                        val receiverConnection = connections[receiverId]
                        if (receiverConnection != null) {
                            receiverConnection.send(messageText)
                        } else {
                            saveMessageToDatabase(senderId, receiverId, content)
                        }
                    }
                }
            } finally {
                connections.remove(senderId)
            }
        }

        //rest api de fetch lich su
        get("/history") {
            val senderId = call.request.queryParameters["senderId"]?.toIntOrNull()
            val receiverId = call.request.queryParameters["receiverId"]?.toIntOrNull()

            if (senderId == null || receiverId == null) {
                call.respond("Missing senderId or receiverId")
                return@get
            }

            val messages = fetchChatHistory(senderId, receiverId)
            call.respond(messages)
        }
    }
}

//luu mess vo db
fun saveMessageToDatabase(senderId: Int, receiverId: Int, content: String) {
    transaction {
        MessagesSchema.insert {
            it[this.senderId] = senderId
            it[this.receiverId] = receiverId
            it[this.content] = content
            it[this.timestamp] = Instant.now().toEpochMilli()
        }
    }
}

//fetch lich su chat tu db
fun fetchChatHistory(senderId: Int, receiverId: Int): List<ChatMessage> {
    return transaction {
        MessagesSchema.selectAll().where {
            (MessagesSchema.senderId eq senderId and (MessagesSchema.receiverId eq receiverId)) or
                    (MessagesSchema.senderId eq receiverId and (MessagesSchema.receiverId eq senderId))
        }.orderBy(MessagesSchema.timestamp, SortOrder.ASC)
            .map {
                ChatMessage(
                    receiverId = it[MessagesSchema.receiverId],
                    content = it[MessagesSchema.content]
                )
            }
    }
}
