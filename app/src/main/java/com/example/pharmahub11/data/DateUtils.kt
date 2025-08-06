package com.example.pharmahub11.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    fun formatDate(timestamp: Long, pattern: String = "MMM d, yyyy"): String {
        return SimpleDateFormat(pattern, Locale.getDefault())
            .format(Date(timestamp))
    }
}