package com.example.database

import org.jetbrains.exposed.sql.Table

object UsersSchema : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 100).nullable().uniqueIndex()
    val password = varchar("password", 100).nullable()
    val email = varchar("email", 64).uniqueIndex().nullable()
    val oauthID = varchar("oauth_id", 128).nullable()
    val isVerified = bool("is_verified").default(false)

    override val primaryKey = PrimaryKey(id)
}