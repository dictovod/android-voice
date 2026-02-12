package com.voicemessenger.model

data class Contact(
    val id: String,
    val name: String,
    val status: String,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false
)