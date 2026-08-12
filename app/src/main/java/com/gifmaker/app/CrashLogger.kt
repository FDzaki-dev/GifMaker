package com.gifmaker.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Crash logger bawaan.
 *
 * - Menangkap uncaught exception via Thread.setDefaultUncaughtExceptionHandler.
 * - Entry point penyimpanan: MediaStore (API 29+) ke Documents/GifMaker/logs/,
 *   tanpa permission storage legacy. Di bawah API 29, fallback ke app-specific
 *   external storage (juga tanpa permission).
 * - Fail-safe: seluruh proses penulisan dibungkus try-catch agar logger sendiri
 *   tidak pernah menyebabkan crash tambahan / loop crash.
 * - FIFO retention: menyimpan maksimal MAX_LOGS file, file tertua dihapus duluan.
 * - Metadata: versi app, OS, model device, timestamp, thread, stack trace.
 */
object CrashLogger {

    private const val APP_FOLDER = "GifMaker"
    private const val MAX_LOGS = 50
    private const val RELATIVE_DIR = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/logs"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLog(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Fail-safe: logger tidak boleh menambah crash baru.
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeLog(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "crash_${timestamp}_${UUID.randomUUID()}.txt"
        val content = buildLogContent(context, thread, throwable, timestamp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, fileName, content)
        } else {
            writeViaLegacyFile(context, fileName, content)
        }
        try {
            enforceFifoRetention(context)
        } catch (_: Throwable) {
            // Fail-safe: kegagalan retention tidak boleh menggagalkan logging utama.
        }
    }

    private fun buildLogContent(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        timestamp: String
    ): String {
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        val versionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } catch (_: Exception) {
            -1L
        }

        return buildString {
            appendLine("=== GifMaker Crash Log ===")
            appendLine("Timestamp: $timestamp")
            appendLine("App Version: $versionName ($versionCode)")
            appendLine("OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendLine(throwable.stackTraceToString())
        }
    }

    private fun writeViaMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }

    private fun writeViaLegacyFile(context: Context, fileName: String, content: String) {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val dir = File(baseDir, "$APP_FOLDER/logs")
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeText(content)
    }

    private fun enforceFifoRetention(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            enforceFifoRetentionMediaStore(context)
        } else {
            enforceFifoRetentionLegacy(context)
        }
    }

    private fun enforceFifoRetentionMediaStore(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("$RELATIVE_DIR/", "crash_%.txt")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val total = cursor.count
            var toDelete = total - MAX_LOGS
            if (toDelete <= 0) return
            while (cursor.moveToNext() && toDelete > 0) {
                val id = cursor.getLong(idColumn)
                val uri = MediaStore.Files.getContentUri("external", id)
                resolver.delete(uri, null, null)
                toDelete--
            }
        }
    }

    private fun enforceFifoRetentionLegacy(context: Context) {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val dir = File(baseDir, "$APP_FOLDER/logs")
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") } ?: return
        if (files.size <= MAX_LOGS) return
        files.sortBy { it.lastModified() }
        files.take(files.size - MAX_LOGS).forEach { it.delete() }
    }
}
