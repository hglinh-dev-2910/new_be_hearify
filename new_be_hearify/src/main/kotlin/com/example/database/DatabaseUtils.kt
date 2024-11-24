package com.example.database

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun addUser(username: String, password: String): Boolean {
    return transaction {
        //kiem tra ton tai
        val exists = UsersSchema.selectAll().where(UsersSchema.username eq username).empty().not()
        if (exists) { //username da ton tai
            println("User $username already exists!")
            return@transaction false
        } else { //them username vao db
            UsersSchema.insert {
                it[UsersSchema.username] = username
                it[UsersSchema.password] = password
            }
            println("User $username added to the database.")
            return@transaction true
        }

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
