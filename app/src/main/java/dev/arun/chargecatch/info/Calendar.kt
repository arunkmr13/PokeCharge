package dev.arun.chargecatch.info

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NextEvent(val title: String, val begin: Long, val end: Long) {
    /** "Team sync · in 25 min", "Team sync · now", or "Team sync · 14:30". */
    fun label(now: Long = System.currentTimeMillis()): String {
        val minutes = ((begin - now) / 60_000L).toInt()
        val whenText = when {
            begin <= now -> "now"
            minutes < 1 -> "in a minute"
            minutes < 60 -> "in $minutes min"
            else -> Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).format(TIME)
        }
        return "$title · $whenText"
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * The next timed event (not all-day, not declined) in the next 24 hours, from every calendar
 * on the phone. Needs the calendar permission; returns null without it.
 */
object CalendarRepository {
    private const val WINDOW_MS = 24 * 60 * 60_000L

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun nextEvent(context: Context): NextEvent? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 60 * 60_000L)
            ContentUris.appendId(it, now + WINDOW_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0 AND " +
            "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.END} > ? AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                NextEvent(
                    title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Event",
                    begin = cursor.getLong(1),
                    end = cursor.getLong(2)
                )
            }
        }.getOrNull()
    }
}
