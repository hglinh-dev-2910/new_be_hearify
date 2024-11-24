package com.example.test

import com.example.database.UsersSchema
import com.example.database.changePassword
import java.util.*

fun testChangePassword() {
    val scanner = Scanner(System.`in`)

    println("Enter username to change password:")
    val username = scanner.nextLine()

    println("Please enter new password for $username:")
    val newPassword = scanner.nextLine()

    //show result
    changePassword(username, newPassword)


}
