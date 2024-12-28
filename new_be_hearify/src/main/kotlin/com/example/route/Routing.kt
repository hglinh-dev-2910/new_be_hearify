package com.example.route

import io.ktor.server.application.*
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

