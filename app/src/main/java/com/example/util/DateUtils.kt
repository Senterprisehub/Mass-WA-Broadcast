package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dateTimeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return "Not scheduled"
        return dateTimeFormat.format(Date(epochMs))
    }

    fun formatTimeOnly(epochMs: Long): String {
        if (epochMs <= 0L) return "--:--"
        return timeOnlyFormat.format(Date(epochMs))
    }

    fun formatDateOnly(epochMs: Long): String {
        if (epochMs <= 0L) return "--"
        return dateOnlyFormat.format(Date(epochMs))
    }

    fun formatDurationSeconds(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun dayBoundsFor(epochMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis - 1L
        return start to end
    }
}
