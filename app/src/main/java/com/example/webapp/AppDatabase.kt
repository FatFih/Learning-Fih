package com.example.webapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [User::class, Lesson::class, LessonFile::class, Exam::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        // v2 added displayName; existing users get their (lowercased) username as display name
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE users SET displayName = username")
            }
        }

        // v3 added the timetable and the uploaded files
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lessons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`username` TEXT NOT NULL, `day` INTEGER NOT NULL, `period` INTEGER NOT NULL, " +
                        "`subject` TEXT NOT NULL, `room` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lesson_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`username` TEXT NOT NULL, `subjectKey` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`storedName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `size` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL)"
                )
            }
        }

        // v4 added exams
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`username` TEXT NOT NULL, `date` TEXT NOT NULL, `period` INTEGER NOT NULL, " +
                        "`subject` TEXT NOT NULL, `details` TEXT NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "dolgozat-naptar.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}