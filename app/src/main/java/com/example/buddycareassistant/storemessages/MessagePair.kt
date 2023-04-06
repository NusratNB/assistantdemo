package com.example.buddycareassistant.storemessages

import android.os.Message

data class MessagePair(val user: String, val userMessage: String,
                       val assistant: String, val assistantMessage: String): java.io.Serializable


data class MessageObj(val messagePairs: MutableList<MessagePair> = mutableListOf()): java.io.Serializable

