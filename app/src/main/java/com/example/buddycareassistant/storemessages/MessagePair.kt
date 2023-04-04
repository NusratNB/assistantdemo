package com.example.buddycareassistant.storemessages

data class MessagePair(val messageIndex:String,
                       val user: String, val userMessage: String,
                       val assistant: String, val assistantMessage: String)

