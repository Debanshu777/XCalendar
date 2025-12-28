package com.debanshu.xcalendar.domain.util

import com.debanshu.xcalendar.data.remoteDataSource.error.DataError

/**
 * Maps data layer errors to domain layer errors.
 */
fun DataError.toDomainError(): DomainError = when (this) {
    DataError.Network.NO_INTERNET -> DomainError.NoInternet
    DataError.Network.SERVER_ERROR -> DomainError.ServerError
    DataError.Network.REQUEST_TIMEOUT -> DomainError.Timeout
    DataError.Network.UNAUTHORIZED -> DomainError.Unauthorized
    DataError.Network.CONFLICT -> DomainError.Unknown("A conflict occurred. Please try again.")
    DataError.Network.SERIALIZATION -> DomainError.Unknown("Failed to process server response.")
    DataError.Network.PAYLOAD_TOO_LARGE -> DomainError.Unknown("Data too large to upload.")
    DataError.Network.UNKNOWN -> DomainError.Unknown("An unexpected error occurred.")
    DataError.Local.DISK_FULL -> DomainError.DatabaseError
}

