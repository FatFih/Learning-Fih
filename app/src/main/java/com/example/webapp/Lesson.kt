package com.example.webapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val day: Int,
    val period: Int,
    val subject: String,
    val room: String
)
