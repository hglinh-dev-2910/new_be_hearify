package com.example.route

import com.example.database.UsersSchema
import com.example.serialization.RegisterRequest
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

                val idToken = request.oauthID ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing token")
                )

                try {
                    val googleTokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=$idToken"
                    val client = HttpClient(CIO)

                    //fetch token
                    val response = client.get(googleTokenInfoUrl)
                    client.close() //close client after use

                    if (response.status == HttpStatusCode.OK) {
                        val jsonResponse = response.bodyAsText()

                        val json = parseToJsonElement(jsonResponse)

                        val oauthID = json.jsonObject["sub"]?.jsonPrimitive?.content
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing oauthID")
                            )
                        val email = json.jsonObject["email"]?.jsonPrimitive?.content
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing email")
                            )

                        //check if user exited

                        val userExits = transaction {
                            UsersSchema.selectAll().where { UsersSchema.email eq email }.count() > 0
                        }

                        if (userExits) {
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "User already exists")
                            )
                        } else {
                            transaction {
                                UsersSchema.insert {
                                    it[this.email] = email
                                    it[this.oauthID] = oauthID
                                }
                            }
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "Registration successful at $email")
                            )
                        }

                    } else {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("message" to "Invalid token")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "An error occurred: ${e.localizedMessage}")
                    )
                }
            }

            else -> {
                call.respondText("Invalid registration method.")
            }
        }
    }
}
