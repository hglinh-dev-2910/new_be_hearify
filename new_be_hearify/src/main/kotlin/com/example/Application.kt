package com.example

import com.example.database.initDatabase
import com.example.database.addUser
import io.ktor.server.application.*
import com.example.test.*

//fun main(args: Array<String>) {
//    io.ktor.server.netty.EngineMain.main(args)
//}

fun Application.module() {
    initDatabase()

    //testMultipleUsers()
    //testChangePassword()
    testDeleteUser()
}
