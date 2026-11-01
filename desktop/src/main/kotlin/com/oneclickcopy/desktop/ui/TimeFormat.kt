package com.oneclickcopy.desktop.ui

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Formats a timestamp as a short relative string ("5m ago", "yesterday"). */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - timestamp).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 7 -> "${days}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}
