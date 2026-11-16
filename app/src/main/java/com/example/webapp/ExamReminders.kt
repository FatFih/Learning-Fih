package com.example.webapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ExamReminders {
    private const val DAYS_BEFORE = 3

    private val MONTHS = arrayOf(
        "január", "február", "március", "április", "május", "június",
        "július", "augusztus", "szeptember", "október", "november", "december"
    )

    private fun workName(examId: Long, daysBefore: Int) = "exam_reminder_${examId}_$daysBefore"

    private fun reminderTime(date: String, daysBefore: Int, time: String): Long {
        val (year, month, day) = date.split("-").map { it.toInt() }
        val (hour, minute) = time.split(":").map { it.toInt() }
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
            add(Calendar.DAY_OF_MONTH, -daysBefore)
        }.timeInMillis
    }

    fun schedule(context: Context, exam: Exam) {
        val workManager = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        val time = AppSettings.reminderTime(context)
        for (daysBefore in DAYS_BEFORE downTo 1) {
            val at = reminderTime(exam.date, daysBefore, time)
            if (at <= now) {
                workManager.cancelUniqueWork(workName(exam.id, daysBefore))
                continue
            }
            workManager.enqueueUniqueWork(
                workName(exam.id, daysBefore),
                ExistingWorkPolicy.REPLACE,
                request(exam.id, daysBefore, at - now)
            )
        }
    }

    suspend fun rescheduleAll(context: Context) {
        AppDatabase.get(context).timetableDao().allExams().forEach { schedule(context, it) }
    }

    private fun request(examId: Long, daysBefore: Int, delayMillis: Long) =
        OneTimeWorkRequestBuilder<ExamReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("examId" to examId, "daysBefore" to daysBefore))
            .build()

    fun cancel(context: Context, examId: Long) {
        val workManager = WorkManager.getInstance(context)
        for (daysBefore in DAYS_BEFORE downTo 1) workManager.cancelUniqueWork(workName(examId, daysBefore))
        workManager.cancelUniqueWork("exam_reminder_$examId")
    }

    fun message(subject: String, daysBefore: Int): String {
        val lastVowel = subject.lowercase().lastOrNull { it in "aáoóuúeéiíöőüű" }
        val suffix = if (lastVowel != null && lastVowel in "aáoóuú") "ból" else "ből"
        return "$daysBefore Nap múlva DOLGOZAT LESZ $subject-$suffix"
    }

    fun details(exam: Exam): String {
        val (_, month, day) = exam.date.split("-").map { it.toInt() }
        val whenText = "${MONTHS[month - 1]} $day., ${exam.period}. óra"
        return if (exam.details.isBlank()) whenText else "$whenText – ${exam.details}"
    }
}

class ExamReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val exam = AppDatabase.get(applicationContext).timetableDao()
            .exam(inputData.getLong("examId", -1)) ?: return Result.success()

        NotificationHelper.createChannel(applicationContext)
        NotificationHelper.show(
            applicationContext,
            (1000 + exam.id).toInt(),
            ExamReminders.message(exam.subject, inputData.getInt("daysBefore", 3)),
            ExamReminders.details(exam)
        )
        return Result.success()
    }
}
