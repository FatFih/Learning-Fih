package com.example.webapp

import androidx.room.Entity
import androidx.room.PrimaryKey

// A file uploaded to a subject. Files belong to the subject (not one lesson),
// so they show up on every lesson of that subject.
@Entity(tableName = "lesson_files")
data class LessonFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val subjectKey: String, // lowercased subject name
    val fileName: String,   // original name, shown to the user
    val storedName: String, // name inside filesDir/lesson-files
    val mimeType: String,
    val size: Long,
    val addedAt: Long
)

data class FileCount(val subjectKey: String, val count: Int)
