package com.stasybell.os

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class TimelineSlice(val start: LocalTime, val end: LocalTime, val visit: VisitItem?)

/** Build a day view from working windows and actual occupied intervals, including buffers. */
fun dayTimeline(day: LocalDate, windows: List<WorkingTime>, visits: List<VisitItem>): List<TimelineSlice> {
    val zone = ZoneId.of("Europe/Moscow")
    val occupied = visits.filter { it.status in setOf("pending_channel", "confirmed", "completed", "no_show", "blocked") }
        .map { visit ->
            Triple(
                OffsetDateTime.parse(visit.startsAt).atZoneSameInstant(zone).toLocalDateTime(),
                OffsetDateTime.parse(visit.occupiedUntil).atZoneSameInstant(zone).toLocalDateTime(),
                visit,
            )
        }.sortedBy { it.first }
    return windows.flatMap { window ->
        val start = LocalDateTime.of(day, LocalTime.parse(window.start))
        val end = LocalDateTime.of(day, LocalTime.parse(window.end))
        val slices = mutableListOf<TimelineSlice>()
        var cursor = start
        occupied.forEach { (occupiedStart, occupiedEnd, visit) ->
            val begin = maxOf(start, occupiedStart, cursor)
            val finish = minOf(end, occupiedEnd)
            if (begin < finish) {
                if (cursor < begin) slices.add(TimelineSlice(cursor.toLocalTime(), begin.toLocalTime(), null))
                slices.add(TimelineSlice(begin.toLocalTime(), finish.toLocalTime(), visit))
                cursor = finish
            }
        }
        if (cursor < end) slices.add(TimelineSlice(cursor.toLocalTime(), end.toLocalTime(), null))
        slices
    }
}
