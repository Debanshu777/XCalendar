package com.debanshu.xcalendar.data.remoteDataSource

import com.debanshu.xcalendar.BuildKonfig
import com.debanshu.xcalendar.data.remoteDataSource.error.DataError
import com.debanshu.xcalendar.data.remoteDataSource.model.holiday.HolidayResponse
import io.ktor.client.HttpClient
import org.koin.core.annotation.Single

@Single
class HolidayApiService(
    client: HttpClient,
) {
    private val clientWrapper = ClientWrapper(client)
    private val baseUrl = "https://calendarific.com/api/v2/holidays"

    suspend fun getHolidays(
        countryCode: String,
        year: Int,
    ): Result<HolidayResponse, DataError> =
        clientWrapper.networkGetUsecase<HolidayResponse>(
            baseUrl,
            mapOf(
                "api_key" to BuildKonfig.API_KEY,
                "country" to countryCode,
                "year" to year.toString(),
            ),
        )
}
