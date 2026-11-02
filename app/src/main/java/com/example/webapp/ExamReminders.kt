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

// Reminds the user every day before an exam: "3 Nap múlva DOLGOZAT LESZ <subject>-ból",
// then "2 Nap múlva...", then "1 Nap múlva...". Nothing on the exam day itself.
// WorkManager keeps the timers through phone restarts.
object ExamReminders {
    // Reminders start this many days before the exam and go off at this hour every day
    // (keep in sync with EXAM_REMINDER_* in calendar-page.js, which shows these times)
    private const val DAYS_BEFORE = 3
    private const val HOUR = 13

    private val MONTHS = arrayOf(
        "január", "február", "március", "április", "május", "június",
        "július", "augusztus", "szeptember", "október", "november", "december"
    )

    // One timer per day: exam_reminder_<exam id>_<days before>
    private fun workName(examId: Long, daysBefore: Int) = "exam_reminder_${examId}_$daysBefore"

    // The moment of the reminder `daysBefore` days before the exam
    private fun reminderTime(date: String, daysBefore: Int): Long {
        val (year, month, day) = date.split("-").map { it.toInt() }
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, HOUR, 0)
            add(Calendar.DAY_OF_MONTH, -daysBefore)
        }.timeInMillis
    }

    // Saving an exam again replaces its old timers. Days that have already passed
    // (exam added late) are skipped, the rest still go off.
    fun schedule(context: Context, exam: Exam) {
        val workManager = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        for (daysBefore in DAYS_BEFORE downTo 1) {
            val at = reminderTime(exam.date, daysBefore)
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

    // Sends the "3 Nap múlva" reminder right now, through the same worker as the real ones
    fun sendTest(context: Context, examId: Long) {
        WorkManager.getInstance(context).enqueue(request(examId, DAYS_BEFORE, 0))
    }

    private fun request(examId: Long, daysBefore: Int, delayMillis: Long) =
        OneTimeWorkRequestBuilder<ExamReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("examId" to examId, "daysBefore" to daysBefore))
            .build()

    fun cancel(context: Context, examId: Long) {
        val workManager = WorkManager.getInstance(context)
        for (daysBefore in DAYS_BEFORE downTo 1) workManager.cancelUniqueWork(workName(examId, daysBefore))
        workManager.cancelUniqueWork("exam_reminder_$examId") // the single timer older versions used
    }

    // "Matematika" -> "...Matematika-ból", "Történelem" -> "...Történelem-ből" (vowel harmony)
    fun message(subject: String, daysBefore: Int): String {
        val lastVowel = subject.lowercase().lastOrNull { it in "aáoóuúeéiíöőüű" }
        val suffix = if (lastVowel != null && lastVowel in "aáoóuú") "ból" else "ből"
        return "$daysBefore Nap múlva DOLGOZAT LESZ $subject-$suffix"
    }

    // "november 6., 3. óra" + the details the user wrote
    fun details(exam: Exam): String {
        val (_, month, day) = exam.date.split("-").map { it.toInt() }
        val whenText = "${MONTHS[month - 1]} $day., ${exam.period}. óra"
        return if (exam.details.isBlank()) whenText else "$whenText – ${exam.details}"
    }
}

class ExamReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The exam may have been deleted since the timer was set
        val exam = AppDatabase.get(applicationContext).timetableDao()
            .exam(inputData.getLong("examId", -1)) ?: return Result.success()

        NotificationHelper.createChannel(applicationContext)
        // Same notification id for every day, so the new reminder replaces yesterday's
        NotificationHelper.show(
            applicationContext,
            (1000 + exam.id).toInt(),
            ExamReminders.message(exam.subject, inputData.getInt("daysBefore", 3)),
            ExamReminders.details(exam)
        )
        return Result.success()
    }
}
