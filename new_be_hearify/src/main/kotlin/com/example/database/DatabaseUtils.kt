package com.example.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun addUser(username: String?, email: String, password: String?, oauthID: String?): Boolean {
    return transaction {
        // Kiểm tra xem email đã tồn tại chưa
        val exists = UsersSchema.selectAll().where { UsersSchema.email eq email }.singleOrNull()
        if (exists != null) {
            println("Email $email has!")
            return@transaction false
        }

        // Thêm người dùng mới
        UsersSchema.insert {
            it[this.username] = username
            it[this.email] = email
            it[this.password] = password?.let { password -> BCrypt.hashpw(password, BCrypt.gensalt()) } // hash pw truoc khi luu
            it[this.oauthID] = oauthID
            it[this.isVerified] = true
        }
        println("User $email added successfully!.")
        return@transaction true
    }
}

fun changePassword(username: String, newPassword: String): Boolean {
    return transaction {
        val userExists = UsersSchema.selectAll().where(UsersSchema.username eq username).empty().not()
        if (userExists) {
            //co username thi cho phep doi mat khau
            UsersSchema.update({UsersSchema.username eq username}) {
                it[password] = newPassword
            }
            println("Password for user $username has been updated.")
            true
        } else { //khong co username
            println("User $username does not exist.")
            false
        }
    }
}

fun deleteUser(username: String): Boolean {
    return transaction {
        val userExists = UsersSchema.selectAll().where(UsersSchema.username eq username).empty().not()
        if (userExists) {
            //co username thi cho xoa
            UsersSchema.deleteWhere { UsersSchema.username eq username }
            println("User $username has been deleted.")
            true
        } else {//khong co username
            println("User $username does not exist.")
            false
        }
    }
}

fun findUserByEmail(email: String): ResultRow? {
    return transaction {
        UsersSchema.selectAll().where { UsersSchema.email eq email }.singleOrNull()
    }
}

fun findUserIdByUsername(username: String): ResultRow? {
    return transaction {
        UsersSchema.selectAll().where { UsersSchema.username eq username }.singleOrNull()
    }
}