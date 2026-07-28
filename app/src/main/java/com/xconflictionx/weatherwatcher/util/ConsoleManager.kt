package com.xconflictionx.weatherwatcher.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ConsoleManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val serverDetail = if (throwable is retrofit2.HttpException) {
            try {
                val errorBody = throwable.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) "\nServer says: $errorBody" else ""
            } catch (e: Exception) { "" }
        } else ""

        val displayMessage = when {
            throwable is java.net.SocketTimeoutException || throwable is kotlinx.coroutines.TimeoutCancellationException -> 
                "$message: Server Busy (Timeout)$serverDetail"
            throwable is java.net.UnknownHostException -> 
                "$message: No Internet / DNS Error"
            throwable is retrofit2.HttpException -> 
                "$message: Server Error (${throwable.code()})$serverDetail"
            else -> "$message$serverDetail"
        }

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = displayMessage,
            error = throwable?.stackTraceToString()
        )
        
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry)
        
        // Keep only last 50 logs
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        
        _logs.value = currentList
        
        // Always mirror to Logcat for standard debugging
        Log.e(tag, "$message: ${throwable?.message}", throwable)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

data class LogEntry(
    val timestamp: Long,
    val tag: String,
    val message: String,
    val error: String? = null
)
