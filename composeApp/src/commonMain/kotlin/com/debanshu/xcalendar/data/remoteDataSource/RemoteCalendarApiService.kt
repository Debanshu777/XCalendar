package com.debanshu.xcalendar.data.remoteDataSource

import com.debanshu.xcalendar.data.remoteDataSource.error.DataError
import com.debanshu.xcalendar.data.remoteDataSource.model.calendar.CalendarResponseItem
import com.debanshu.xcalendar.data.remoteDataSource.model.calendar.EventResponseItem
import io.ktor.client.HttpClient
import org.koin.core.annotation.Single

@Single
class RemoteCalendarApiService(
    client: HttpClient,
) {
    private val clientWrapper = ClientWrapper(client)
    private val baseUrl = "https://raw.githubusercontent.com/Debanshu777/XCalendar/main/"

    suspend fun fetchCalendarsForUser(userId: String): Result<List<CalendarResponseItem>, DataError> =
        clientWrapper.networkGetUsecase<List<CalendarResponseItem>>(
            baseUrl + "assets/calendars.json",
            mapOf(
                "user_id" to userId,
            ),
        )

    suspend fun fetchEventsForCalendar(
        calendarIds: List<String>,
        startTime: Long,
        endTime: Long,
    ): Result<List<EventResponseItem>, DataError> =
        clientWrapper.networkGetUsecase<List<EventResponseItem>>(
            baseUrl + "assets/events.json",
            mapOf(
                "calendar_ids" to calendarIds.toString(),
                "start_time" to startTime.toString(),
                "end_time" to endTime.toString(),
            ),
        )
}
