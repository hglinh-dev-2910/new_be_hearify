package com.example.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object MessagesSchema : Table("messages") {
    val id = integer("id").autoIncrement()

    val senderId = integer("sender_id").references(
        UsersSchema.id, onDelete = ReferenceOption.CASCADE
    )

    val receiverId = integer("receiver_id").references(
        UsersSchema.id, onDelete = ReferenceOption.CASCADE
    )

    val content = text("content")

    // Thời gian gửi tin nhắn (sử dụng timestamp với API JavaTime)
    val timestamp = datetime("timestamp")

    val isRead = bool("is_read").default(false)

    val status = varchar("status", 10).default("pending") //for pending sent

    init {
        // Composite index cho sender_id và receiver_id (history query)
        index(isUnique = false, senderId, receiverId)

        // Composite index cho receiver_id và is_read (pending sent)
        index(isUnique = false, receiverId, isRead)

        // Index sắp xếp theo timestamp (timestamp)
        index(isUnique = false, timestamp)
    }

    override val primaryKey = PrimaryKey(id)
}