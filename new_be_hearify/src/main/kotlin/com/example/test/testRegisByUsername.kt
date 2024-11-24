package com.example.test

import java.util.Scanner
import com.example.database.*
fun testMultipleUsers() {
    val scanner = Scanner(System.`in`)

    println("Numb: ")
    val numberOfUsers = scanner.nextInt()
    scanner.nextLine()

    for (i in 1..numberOfUsers) {
        println("Username $i:")
        val username = scanner.nextLine()

        println("Password $i:")
        val password = scanner.nextLine()

        //show result
        addUser(username, password)

    }
}
