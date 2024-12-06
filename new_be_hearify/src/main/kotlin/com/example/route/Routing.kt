package com.example.route

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    routing {
        registerRoutes()
        loginRoutes()
        verifyRoutes()
        matchRoutes()
        messageRoutes()
    }
}

