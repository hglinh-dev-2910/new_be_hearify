package com.example.route

import com.example.database.MatchSchema
import com.example.serialization.MatchRequest
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.matchRoutes() {
    post("/match/send") {
        val request = try {
            call.receive<MatchRequest>()
        } catch (e: Exception) {
            call.respondText("Invalid JSON format")
            return@post
        }

        val senderID = request.senderID.toInt()
        val receiverID = request.receiverID.toInt()

        val isRequestPending = transaction {
            MatchSchema.selectAll().where {
                (
                        (
                                (
                                        (MatchSchema.senderID eq senderID) and (MatchSchema.receiverID eq receiverID))
                                        or ((MatchSchema.receiverID eq senderID) and (MatchSchema.senderID eq receiverID))
                                )
                                and (MatchSchema.status eq "pending")
                        )
            }.count() > 0
        }

        if (isRequestPending) {
            call.respondText("Match request already sent and waiting for response.")
            return@post
        }

        transaction {
            MatchSchema.insert {
                it[this.senderID] = senderID
                it[this.receiverID] = receiverID
                it[this.status] = "pending"
                it[this.createdAt] = System.currentTimeMillis()
            }
        }

        call.respondText("Match request sent successfully")
    }

    post("/match/accept") {
        val request = try {
            call.receive<MatchRequest>()
        } catch (e: Exception) {
            call.respondText("Invalid JSON format")
            return@post
        }

        val senderID = request.senderID.toInt()
        val receiverID = request.receiverID.toInt()

        val rowsUpdated = transaction {
            MatchSchema.update({
                MatchSchema.senderID eq senderID and (MatchSchema.receiverID eq receiverID) and (MatchSchema.status eq "pending")
            }) {
                it[this.status] = "accepted"
            }
        }

        if (rowsUpdated > 0) {
            call.respondText("Match request accepted")
        } else {
            call.respondText("No pending match request found to accept")
        }
    }

    post("/match/reject") {
        val request = try {
            call.receive<MatchRequest>()
        } catch (e: Exception) {
            call.respondText("Invalid JSON format")
            return@post
        }

        val senderID = request.senderID.toInt()
        val receiverID = request.receiverID.toInt()

        val rowsUpdated = transaction {
            MatchSchema.update({
                MatchSchema.senderID eq senderID and (MatchSchema.receiverID eq receiverID) and (MatchSchema.status eq "pending")
            }) {
                it[this.status] = "rejected"
            }
        }

        if (rowsUpdated > 0) {
            call.respondText("Match request rejected")
        } else {
            call.respondText("No pending match request found to reject")
        }
    }

    get("/match/list/{userID}") {
        val userID = call.parameters["userID"]?.toIntOrNull()

        if (userID == null) {
            call.respondText("Invalid userID")
            return@get
        }

        val matches = transaction {
            MatchSchema.selectAll().where { (MatchSchema.senderID eq userID) or (MatchSchema.receiverID eq userID) }
                .map {
                    mapOf(
                        "id" to it[MatchSchema.id],
                        "senderID" to it[MatchSchema.senderID],
                        "receiverID" to it[MatchSchema.receiverID],
                        "status" to it[MatchSchema.status],
                        "createdAt" to it[MatchSchema.createdAt]
                    )
                }
        }

        call.respond(matches)
    }
}
