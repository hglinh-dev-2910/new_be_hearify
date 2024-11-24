package com.example.test

import com.example.database.deleteUser
import java.util.*

fun testDeleteUser() {
    val scanner = Scanner(System.`in`)
    println("Please enter username to delete:")
    val username = scanner.nextLine()

    deleteUser(username)
}
