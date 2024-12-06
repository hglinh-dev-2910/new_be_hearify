package com.example.database

import org.jetbrains.exposed.sql.Table

object MessagesSchema : Table("messages") {
    val id = integer("id").autoIncrement()
    val senderId = integer("sender_id").references(UsersSchema.id)
    val receiverId = integer("receiver_id").references(UsersSchema.id)
    val content = text("content")
    val timestamp = long("timestamp")
    val isRead = bool("is_read").default(false)

    override val primaryKey = PrimaryKey(id)
}
