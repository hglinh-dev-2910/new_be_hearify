package com.example.database

import org.jetbrains.exposed.sql.Table

object UsersSchema : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 64)
    override val primaryKey = PrimaryKey(id)
}