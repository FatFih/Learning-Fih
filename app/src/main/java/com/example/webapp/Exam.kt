package com.example.webapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val date: String,
    val period: Int,
    val subject: String,
    val details: String
)
