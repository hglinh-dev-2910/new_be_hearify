package com.example.database

import org.jetbrains.exposed.sql.Table

object MatchSchema : Table("matches") {
    val id = integer("id").autoIncrement()
    val senderID = integer("sender_id").references(UsersSchema.id)
    val receiverID = integer("receiver_id").references(UsersSchema.id)
    val status = varchar("status", 20) // "pending", "accepted", or "rejected"
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
