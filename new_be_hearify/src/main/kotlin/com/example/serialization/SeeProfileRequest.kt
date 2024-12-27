package com.example.serialization

import kotlinx.serialization.Serializer

@Serializer
data class ProfileRequest(val username: String,
                          val password: String)