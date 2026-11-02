package com.example.webapp

import androidx.room.Entity
import androidx.room.PrimaryKey

// A test / exam on one class of one specific date
@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val date: String,    // "2026-11-06"
    val period: Int,     // which class of that day (1..9)
    val subject: String, // the class it belongs to - the reminder names this subject
    val details: String  // topic, notes (may be empty)
)
