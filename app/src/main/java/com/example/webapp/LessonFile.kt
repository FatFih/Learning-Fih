package com.example.webapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lesson_files")
data class LessonFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val subjectKey: String,
    val fileName: String,
    val storedName: String,
    val mimeType: String,
    val size: Long,
    val addedAt: Long
)

data class FileCount(val subjectKey: String, val count: Int)
