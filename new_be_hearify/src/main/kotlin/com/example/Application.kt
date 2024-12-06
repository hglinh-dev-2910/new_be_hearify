package com.example

import com.example.database.initDatabase
import com.example.route.configureRouting
import com.example.serialization.configureSerialization
import io.ktor.server.application.*
import io.ktor.server.websocket.*


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    initDatabase()

    //config cac thanh phan cua application
    //configureSecurity()

    install(WebSockets)
    //installed negotiation trong serialization

    configureRouting()
    configureSerialization()





    //test cac utils cua db

    //testMultipleUsers()
    //testChangePassword()
    //testDeleteUser()
}
