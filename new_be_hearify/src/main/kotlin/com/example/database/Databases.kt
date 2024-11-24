package com.example.database

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File


fun initDatabase() {
    //kiem tra da tao db chua
    val dbFile = File("hearify.db")
    if (!dbFile.exists()) {
        //connect neu chua co db thi khoi tao
        Database.connect(
            url = "jdbc:sqlite:hearify.db",
            driver = "org.sqlite.JDBC"
        )
        //khoi tao cac table
        transaction {
            SchemaUtils.create(UsersSchema)
        }
        println("Database created")
    } else {
        //neu co roi thi connect lai
        Database.connect(
            url = "jdbc:sqlite:hearify.db",
            driver = "org.sqlite.JDBC"
        )
        println("Database connected")
    }
}