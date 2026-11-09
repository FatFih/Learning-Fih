package com.example.webapp

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class WebAppInterface(
    private val activity: ComponentActivity,
    private val webView: WebView
) {
    private val dao = AppDatabase.get(activity).userDao()
    private val prefs = activity.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val timetable = AppDatabase.get(activity).timetableDao()

    // The file picker answers later, so remember who asked. Registered here because
    // this object is created in MainActivity.onCreate (pickers must be registered that early).
    private var pendingPick: Pair<String, String>? = null // callId, subject
    private val filePicker = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onFilePicked(uri) }

    private fun currentUser(): String? = prefs.getString("logged_in_username", null)

    @JavascriptInterface
    fun register(callId: String, username: String, password: String) {
        activity.lifecycleScope.launch {
            sendResult(callId, AuthRepository.register(dao, username, password))
        }
    }

    @JavascriptInterface
    fun login(callId: String, username: String, password: String) {
        activity.lifecycleScope.launch {
            val user = AuthRepository.login(dao, username, password)
            if (user != null) {
                prefs.edit()
                    .putString("logged_in_username", user.username)
                    .putString("logged_in_display_name", user.displayName)
                    .apply()
            }
            sendResult(callId, user != null)
        }
    }

    @JavascriptInterface
    fun isLoggedIn(): Boolean = prefs.getString("logged_in_username", null) != null

    // The name as it was typed at registration (falls back to the login name)
    @JavascriptInterface
    fun getUsername(): String =
        prefs.getString("logged_in_display_name", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("logged_in_username", "") ?: ""

    @JavascriptInterface
    fun logout() {
        prefs.edit().remove("logged_in_username").remove("logged_in_display_name").apply()
    }

    private fun requestNotificationPermission() {
        if (NotificationHelper.hasPermission(activity) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        activity.runOnUiThread {
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
    }

    // ===== Timetable (calendar page) =====

    // Answers {"lessons": [{day, period, subject, room}], "fileCounts": {"subject key": n},
    //          "exams": [{id, date, period, subject, details}], "reminderTime": "13:00"}
    @JavascriptInterface
    fun getTimetable(callId: String) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendJson(callId, "null")
            val lessons = JSONArray()
            timetable.lessons(user).forEach {
                lessons.put(
                    JSONObject()
                        .put("day", it.day)
                        .put("period", it.period)
                        .put("subject", it.subject)
                        .put("room", it.room)
                )
            }
            val counts = JSONObject()
            timetable.fileCounts(user).forEach { counts.put(it.subjectKey, it.count) }
            val exams = JSONArray()
            timetable.exams(user).forEach {
                exams.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("date", it.date)
                        .put("period", it.period)
                        .put("subject", it.subject)
                        .put("details", it.details)
                )
            }
            sendJson(
                callId,
                JSONObject()
                    .put("lessons", lessons)
                    .put("fileCounts", counts)
                    .put("exams", exams)
                    .put("reminderTime", AppSettings.reminderTime(activity))
                    .toString()
            )
        }
    }

    // lessonsJson: [{period, subject, room}] - replaces everything on that day
    @JavascriptInterface
    fun saveDay(callId: String, day: Int, lessonsJson: String) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendResult(callId, false)
            val lessons = try {
                val array = JSONArray(lessonsJson)
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    Lesson(
                        username = user,
                        day = day,
                        period = item.getInt("period"),
                        subject = item.getString("subject").trim().take(60),
                        room = item.optString("room").trim().take(20)
                    )
                }
            } catch (e: JSONException) {
                return@launch sendResult(callId, false)
            }
            val valid = day in 1..7 &&
                lessons.all { it.period in 1..9 && it.subject.isNotBlank() } &&
                lessons.map { it.period }.toSet().size == lessons.size
            if (valid) timetable.replaceDay(user, day, lessons)
            sendResult(callId, valid)
        }
    }

    // ===== Exams =====

    // Adds the exam, or updates it if that class on that date already has one,
    // and sets its reminder (3 days before)
    @JavascriptInterface
    fun saveExam(callId: String, date: String, period: Int, subject: String, details: String) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendResult(callId, false)
            val cleanSubject = subject.trim().take(60)
            if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || period !in 1..9 || cleanSubject.isBlank()) {
                return@launch sendResult(callId, false)
            }
            val existing = timetable.examAt(user, date, period)
            val exam = Exam(
                id = existing?.id ?: 0,
                username = user,
                date = date,
                period = period,
                subject = cleanSubject,
                details = details.trim().take(500)
            )
            val saved = if (existing != null) {
                timetable.updateExam(exam)
                exam
            } else {
                exam.copy(id = timetable.insertExam(exam))
            }
            ExamReminders.schedule(activity, saved)
            // The reminder needs notification permission (Android 13+)
            requestNotificationPermission()
            sendResult(callId, true)
        }
    }

    @JavascriptInterface
    fun deleteExam(callId: String, examId: Long) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendResult(callId, false)
            val exam = timetable.exam(examId)?.takeIf { it.username == user }
                ?: return@launch sendResult(callId, false)
            timetable.deleteExam(exam)
            ExamReminders.cancel(activity, exam.id)
            sendResult(callId, true)
        }
    }

    // ===== Settings (Beállítások page) =====

    // Answers {"reminderTime": "13:00"}
    @JavascriptInterface
    fun getSettings(callId: String) {
        sendJson(callId, JSONObject().put("reminderTime", AppSettings.reminderTime(activity)).toString())
    }

    // time = "HH:MM". Moves the reminders of every saved exam to the new time.
    @JavascriptInterface
    fun setReminderTime(callId: String, time: String) {
        activity.lifecycleScope.launch {
            val saved = AppSettings.setReminderTime(activity, time)
            if (saved) ExamReminders.rescheduleAll(activity)
            sendResult(callId, saved)
        }
    }

    // Called by theme.js on every page: the status / navigation bar area follows the theme
    // (dark icons on the light sky, light icons on the night sky)
    @JavascriptInterface
    fun setLightTheme(light: Boolean) {
        activity.runOnUiThread {
            (webView.parent as? View)?.setBackgroundColor(if (light) 0xFFBFE3FA.toInt() else 0xFF050505.toInt())
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    // Answers [{id, name, size}] for the files of one subject
    @JavascriptInterface
    fun getFiles(callId: String, subject: String) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendJson(callId, "[]")
            val files = JSONArray()
            timetable.files(user, LessonFileStore.subjectKey(subject)).forEach {
                files.put(JSONObject().put("id", it.id).put("name", it.fileName).put("size", it.size))
            }
            sendJson(callId, files.toString())
        }
    }

    // Opens the system file picker (PDF, DOCX, PPT, PPTX only).
    // Answers {"ok": true} / {"ok": false, "error": "..."} / {"ok": false, "cancelled": true}
    @JavascriptInterface
    fun pickFile(callId: String, subject: String) {
        activity.runOnUiThread {
            pendingPick?.let { (oldCallId, _) -> sendPickResult(oldCallId, cancelled = true) }
            pendingPick = callId to subject
            filePicker.launch(LessonFileStore.MIME_TYPES)
        }
    }

    private fun onFilePicked(uri: Uri?) {
        val (callId, subject) = pendingPick ?: return
        pendingPick = null
        val user = currentUser()
        if (uri == null || user == null) return sendPickResult(callId, cancelled = true)

        activity.lifecycleScope.launch {
            val result = LessonFileStore.import(activity, uri, user, subject)
            val file = result.file
            if (file != null) {
                timetable.insertFile(file)
                sendPickResult(callId)
            } else {
                sendPickResult(callId, error = result.error)
            }
        }
    }

    private fun sendPickResult(callId: String, error: String? = null, cancelled: Boolean = false) {
        val json = JSONObject().put("ok", error == null && !cancelled)
        if (error != null) json.put("error", error)
        if (cancelled) json.put("cancelled", true)
        sendJson(callId, json.toString())
    }

    // Opens the file in another app; false if no app on the phone can open it
    @JavascriptInterface
    fun openFile(callId: String, fileId: Long) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendResult(callId, false)
            val file = timetable.file(fileId, user) ?: return@launch sendResult(callId, false)
            sendResult(callId, LessonFileStore.open(activity, file))
        }
    }

    @JavascriptInterface
    fun deleteFile(callId: String, fileId: Long) {
        activity.lifecycleScope.launch {
            val user = currentUser() ?: return@launch sendResult(callId, false)
            val file = timetable.file(fileId, user) ?: return@launch sendResult(callId, false)
            timetable.deleteFile(file)
            LessonFileStore.delete(activity, file)
            sendResult(callId, true)
        }
    }

    private fun sendResult(callId: String, success: Boolean) = sendJson(callId, success.toString())

    // json must be a JSON value; JSON is valid JavaScript, so it can be passed in directly
    private fun sendJson(callId: String, json: String) {
        if (!callId.matches(Regex("[0-9]{1,10}"))) return
        webView.post {
            webView.evaluateJavascript("window.onNativeResult($callId, $json)", null)
        }
    }
}