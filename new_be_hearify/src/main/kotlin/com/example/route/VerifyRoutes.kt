package com.example.route

import com.example.database.UsersSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun Route.verifyRoutes() {
    post("/verify-email") {
        val params = call.receiveParameters()
        val email = params["email"] ?: return@post call.respondText("Missing email")

        val result = transaction {
            val userExists = UsersSchema.selectAll().where { UsersSchema.email eq email }.empty().not()

            if (userExists) {
                UsersSchema.update({ UsersSchema.email eq email }) {
                    it[this.isVerified] = true
                }
                true
            } else {
                false
            }
        }

        if (result) {
            call.respondText("Email $email has been verified.")
        } else {
            call.respondText("Verification failed: email does not exist.")
        }
    }
}
