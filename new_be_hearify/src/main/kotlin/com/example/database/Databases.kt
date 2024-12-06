package com.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File


fun initDatabase() {
    val dbFile = File("hearify.db")
    if (!dbFile.exists()) {
        Database.connect(
            url = "jdbc:sqlite:hearify.db",
            driver = "org.sqlite.JDBC"
        )
        transaction {
            SchemaUtils.create(UsersSchema, MatchSchema, MessagesSchema)
        }
        println("Database created")
    } else {
        Database.connect(
            url = "jdbc:sqlite:hearify.db",
            driver = "org.sqlite.JDBC"
        )
        println("Database connected")
    }
}