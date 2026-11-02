package com.example.webapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    // The name exactly as typed at registration (username is the lowercased login key)
    @ColumnInfo(defaultValue = "") val displayName: String,
    val passwordSalt: String,
    val passwordHash: String
)