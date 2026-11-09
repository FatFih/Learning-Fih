package com.example.webapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class TimetableDao {
    @Query("SELECT * FROM lessons WHERE username = :username ORDER BY day, period")
    abstract suspend fun lessons(username: String): List<Lesson>

    @Query("DELETE FROM lessons WHERE username = :username AND day = :day")
    abstract suspend fun deleteDay(username: String, day: Int)

    @Insert
    abstract suspend fun insertLessons(lessons: List<Lesson>)

    // Saving a day replaces everything that was on it
    @Transaction
    open suspend fun replaceDay(username: String, day: Int, lessons: List<Lesson>) {
        deleteDay(username, day)
        insertLessons(lessons)
    }

    @Query("SELECT * FROM lesson_files WHERE username = :username AND subjectKey = :subjectKey ORDER BY addedAt")
    abstract suspend fun files(username: String, subjectKey: String): List<LessonFile>

    @Query("SELECT subjectKey, COUNT(*) AS count FROM lesson_files WHERE username = :username GROUP BY subjectKey")
    abstract suspend fun fileCounts(username: String): List<FileCount>

    @Query("SELECT * FROM lesson_files WHERE id = :id AND username = :username LIMIT 1")
    abstract suspend fun file(id: Long, username: String): LessonFile?

    @Insert
    abstract suspend fun insertFile(file: LessonFile): Long

    @Delete
    abstract suspend fun deleteFile(file: LessonFile)

    @Query("SELECT * FROM exams WHERE username = :username ORDER BY date, period")
    abstract suspend fun exams(username: String): List<Exam>

    @Query("SELECT * FROM exams WHERE username = :username AND date = :date AND period = :period LIMIT 1")
    abstract suspend fun examAt(username: String, date: String, period: Int): Exam?

    // Every user's exams (the reminders of all of them move when the reminder time changes)
    @Query("SELECT * FROM exams")
    abstract suspend fun allExams(): List<Exam>

    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    abstract suspend fun exam(id: Long): Exam?

    @Insert
    abstract suspend fun insertExam(exam: Exam): Long

    @Update
    abstract suspend fun updateExam(exam: Exam)

    @Delete
    abstract suspend fun deleteExam(exam: Exam)
}
