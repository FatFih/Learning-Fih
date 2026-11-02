package com.example.webapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Copies picked files into the app's private storage and opens them in other apps
object LessonFileStore {
    private const val MAX_SIZE = 25L * 1024 * 1024
    private const val TOO_BIG = "A fájl túl nagy (legfeljebb 25 MB)."

    private val MIME_BY_EXTENSION = mapOf(
        "pdf" to "application/pdf",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )

    // What the system file picker is allowed to show
    val MIME_TYPES: Array<String> = MIME_BY_EXTENSION.values.toTypedArray()

    class ImportResult(val file: LessonFile? = null, val error: String? = null)

    private class TooBigException : Exception()

    private fun dir(context: Context) = File(context.filesDir, "lesson-files").apply { mkdirs() }

    fun subjectKey(subject: String) = subject.trim().lowercase(Locale.ROOT)

    suspend fun import(context: Context, uri: Uri, username: String, subject: String): ImportResult =
        withContext(Dispatchers.IO) {
            var name = "fajl"
            var reportedSize = -1L
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    if (!cursor.isNull(1)) reportedSize = cursor.getLong(1)
                }
            }

            // Some file managers ignore the picker's type filter, so check the extension too
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = MIME_BY_EXTENSION[extension]
                ?: return@withContext ImportResult(error = "Csak PDF, DOCX, PPT vagy PPTX fájl tölthető fel.")
            if (reportedSize > MAX_SIZE) return@withContext ImportResult(error = TOO_BIG)

            val storedName = "${UUID.randomUUID()}.$extension"
            val target = File(dir(context), storedName)
            try {
                var copied = 0L
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportResult(error = "Nem sikerült beolvasni a fájlt.")
                input.use {
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            // The reported size can be missing or wrong, so count while copying
                            if (copied > MAX_SIZE) throw TooBigException()
                            output.write(buffer, 0, read)
                        }
                    }
                }
                ImportResult(
                    LessonFile(
                        username = username,
                        subjectKey = subjectKey(subject),
                        fileName = name,
                        storedName = storedName,
                        mimeType = mimeType,
                        size = copied,
                        addedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: TooBigException) {
                target.delete()
                ImportResult(error = TOO_BIG)
            } catch (e: Exception) {
                target.delete()
                ImportResult(error = "Nem sikerült beolvasni a fájlt.")
            }
        }

    // Opens the file in an app that can show it (PDF reader, Word, PowerPoint...)
    fun open(context: Context, file: LessonFile): Boolean {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.files", File(dir(context), file.storedName)
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun delete(context: Context, file: LessonFile) {
        File(dir(context), file.storedName).delete()
    }
}
