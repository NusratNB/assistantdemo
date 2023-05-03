package com.example.buddycareassistant.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


object LogUtil {
    private const val LOG_FILE_NAME = "app_logs.txt"
    fun v(context: Context, tag: String, message: String) {
        Log.v(tag, message)
        writeToFile(context, "VERBOSE", tag, message)
    }

    fun d(context: Context, tag: String, message: String) {
        Log.d(tag, message)
        writeToFile(context, "DEBUG", tag, message)
    }

    fun i(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        writeToFile(context, "INFO", tag, message)
    }

    fun w(context: Context, tag: String, message: String) {
        Log.w(tag, message)
        writeToFile(context, "WARN", tag, message)
    }

    fun e(context: Context, tag: String, message: String) {
        Log.e(tag, message)
        writeToFile(context, "ERROR", tag, message)
    }

    private fun writeToFile(context: Context, logType: String, tag: String, message: String) {
        try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            val writer = FileWriter(logFile, true)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val logTimestamp = dateFormat.format(Date())
            writer.append("$logTimestamp - $logType/$tag: $message\n")
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            Log.e("LogUtil", "Error writing log to file: ", e)
        }
    }

    private fun getLogFile(context: Context): File {
        val externalStorageDir = context.getExternalFilesDir(null)
        return File(externalStorageDir, LOG_FILE_NAME)
    }
}
