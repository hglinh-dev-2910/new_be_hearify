package com.example.route

import com.example.database.UsersSchema
import com.example.serialization.RegisterRequest
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun Route.registerRoutes() {
    post("/register") {
        val request = try {
            call.receive<RegisterRequest>() //json
        } catch (e: Exception) {
            call.respondText("Invalid JSON format")
            return@post
        }

        when (request.method) {
            "traditional" -> {
                val username = request.username ?: return@post call.respondText("Missing username")
                val password = request.password ?: return@post call.respondText("Missing password")
                //val email = request.email

                val result = transaction {
                    //val emailExists = UsersSchema.selectAll().where { UsersSchema.email eq email }.empty().not()
                    val usernameExists =
                        UsersSchema.selectAll().where { UsersSchema.username eq username }.empty().not()

                    if (usernameExists/* || emailExists*/) { //cap nhat email sau khi validate
                        false
                    } else {
                        UsersSchema.insert {
                            it[this.username] = username
                            it[this.password] = BCrypt.hashpw(password, BCrypt.gensalt())
                            //it[this.email] = email
                        }
                        true
                    }
                }

                if (result) {
                    call.respondText("Register successful with $username")
                } else {
                    call.respondText("Registration failed: username already exists.")
                }
            }

            "oauth" -> {
                val email = request.email ?: return@post call.respondText("Missing email")
                val oauthID = request.oauthID ?: return@post call.respondText("Missing oauthID")

                val result = transaction {
                    val emailExists = UsersSchema.selectAll().where { UsersSchema.email eq email }.empty().not()

                    if (emailExists) {
                        false
                    } else {
                        UsersSchema.insert {
                            it[this.email] = email
                            it[this.oauthID] = oauthID
                        }
                        true
                    }
                }

                if (result) {
                    call.respondText("Register successful with email $email")
                } else {
                    call.respondText("Registration failed: email already exists.")
                }
            }

            else -> {
                call.respondText("Invalid registration method.")
            }
        }
    }
}
