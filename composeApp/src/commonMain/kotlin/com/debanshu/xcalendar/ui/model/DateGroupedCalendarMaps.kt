package com.debanshu.xcalendar.ui.model

import androidx.compose.runtime.Stable
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

/**
 * Stable wrapper so Compose treats the grouped map as a single parameter with
 * stable identity semantics when the same [map] instance is reused (audit F35).
 */
@Stable
data class EventsByDate(val map: ImmutableMap<LocalDate, ImmutableList<Event>>) {
    operator fun get(date: LocalDate): ImmutableList<Event> = map[date] ?: persistentListOf()
}

@Stable
data class HolidaysByDate(val map: ImmutableMap<LocalDate, ImmutableList<Holiday>>) {
    operator fun get(date: LocalDate): ImmutableList<Holiday> = map[date] ?: persistentListOf()
}
