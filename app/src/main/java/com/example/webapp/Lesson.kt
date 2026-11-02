package com.example.webapp

import androidx.room.Entity
import androidx.room.PrimaryKey

// One class in a user's weekly timetable (the same every week)
@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val day: Int,    // 1 = Monday ... 7 = Sunday
    val period: Int, // 1..9, the times belong to the period (see calendar-page.js)
    val subject: String,
    val room: String
)
