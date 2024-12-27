package com.example.route

import LoginRequest
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.database.UsersSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt


fun Route.loginRoutes() {
    post("/login") {
        try {
            val request = call.receive<LoginRequest>()
            println("request: $request")

            when (request.method) {
                "traditional" -> {
                    val username = request.username
                    val password = request.password

                    if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
                        call.respondText("Missing username or password.")
                        return@post
                    }

                    val user = transaction {
                        UsersSchema.selectAll().where { UsersSchema.username eq username }.singleOrNull()
                    }
                    println("user: $user")

                    if (user != null) {
                        val storedPassword = user[UsersSchema.password]
                        println("storedPassword: $storedPassword")

                        // Lấy giá trị Int của userId
                        val userId = user[UsersSchema.id].value
                        println("userId: $userId")

                        val isValid = storedPassword != null && BCrypt.checkpw(password, storedPassword)
                        println("isValid: $isValid")

                        if (isValid) {
                            val token = JWT.create()
                                .withAudience("jwt-audience")
                                .withIssuer("https://jwt-provider-domain/")
                                .withClaim("userId", userId)
                                .sign(Algorithm.HMAC256("secret"))

                            println("Token :$token  |   userId:$userId")

                            call.respond(
                                mapOf(
                                    "token" to token,
                                    "userId" to userId,
                                    "message" to "Login successful."
                                )
                            )

                        } else {
                            call.respondText("Invalid username or password.")
                        }
                    } else {
                        call.respondText("Invalid username or password.")
                    }
                }

                "oauth" -> {
                    val email = request.email
                    val oauthID = request.oauthID

                    if (email.isNullOrEmpty() || oauthID.isNullOrEmpty()) {
                        call.respondText("Missing email or OAuth ID.")
                        return@post
                    }

                    val user = transaction {
                        UsersSchema.selectAll()
                            .where { UsersSchema.email eq email and (UsersSchema.oauthID eq oauthID) }
                            .singleOrNull()
                    }

                    if (user != null) {
                        // Lấy giá trị Int của userId
                        val userId = user[UsersSchema.id].value

                        val token = JWT.create()
                            .withAudience("jwt-audience")
                            .withIssuer("https://jwt-provider-domain/")
                            .withClaim("userId", userId)
                            .withClaim("email", email)
                            .sign(Algorithm.HMAC256("secret"))

                        call.respond(
                            mapOf(
                                "token" to token,
                                "userId" to userId,
                                "message" to "Login successful."
                            )
                        )
                    } else {
                        call.respondText("No account found for the provided email and OAuth ID.")
                    }
                }

                else -> {
                    call.respondText("Invalid login method.")
                }
            }
        } catch (e: Exception) {
            call.respondText("Invalid JSON request format.")
        }
    }
}