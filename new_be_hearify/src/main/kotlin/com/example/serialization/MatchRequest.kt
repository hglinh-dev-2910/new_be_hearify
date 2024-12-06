package com.example.serialization

import kotlinx.serialization.Serializable


@Serializable
data class MatchRequest(
    val id: Int,
    val senderID: String,
    val receiverID: String,
    val status: String,
    val timeStamp: String // ISO 8601 format, vd: "2024-12-04T12:34:56Z"
)