package com.example.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun sendFriendRequest(senderId: Int, receiverId: Int): Boolean {
    return transaction {
        //check sent request
        val existingRequest = MatchSchema.selectAll().where {
            (MatchSchema.senderID eq senderId and (MatchSchema.receiverID eq receiverId)) or
                    (MatchSchema.senderID eq receiverId and (MatchSchema.receiverID eq senderId))
        }.firstOrNull()

        if (existingRequest == null) {
            //request
            MatchSchema.insert {
                it[this.senderID] = senderId
                it[this.receiverID] = receiverId
                it[this.status] = "pending"
                it[this.createdAt] = System.currentTimeMillis()
            }
            true
        } else {
            false
        }
    }
}

fun acceptFriendRequest(senderId: Int, receiverId: Int): Boolean {
    return transaction {
        val updatedRows = MatchSchema.update(
            where = { (MatchSchema.senderID eq senderId) and (MatchSchema.receiverID eq receiverId) and (MatchSchema.status eq "pending") }
        ) {
            it[this.status] = "accepted"
        }
        updatedRows > 0 // update thanh cong
    }
}

fun rejectFriendRequest(senderId: Int, receiverId: Int): Boolean {
    return transaction {
        val updatedRows = MatchSchema.update(
            where = { (MatchSchema.senderID eq senderId) and (MatchSchema.receiverID eq receiverId) and (MatchSchema.status eq "pending") }
        ) {
            it[this.status] = "rejected"
        }
        updatedRows > 0 // rejected
    }
}

fun listFriends(userId: Int): List<Int> {
    return transaction {
        MatchSchema.selectAll()
            .where { ((MatchSchema.senderID eq userId) or (MatchSchema.receiverID eq userId)) and (MatchSchema.status eq "accepted") }
            .map {
                if (it[MatchSchema.senderID] == userId) it[MatchSchema.receiverID] else it[MatchSchema.senderID]
            }
    }
}

fun listPendingRequests(userId: Int): List<Map<String, Int>> {
    return transaction {
        MatchSchema.selectAll()
            .where { (MatchSchema.receiverID eq userId) and (MatchSchema.status eq "pending") }
            .map {
                mapOf(
                    "senderID" to it[MatchSchema.senderID],
                    "matchID" to it[MatchSchema.id]
                )
            }
    }
}

