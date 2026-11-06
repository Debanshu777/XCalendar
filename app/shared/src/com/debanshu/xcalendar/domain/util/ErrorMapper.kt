package com.debanshu.xcalendar.domain.util

import com.debanshu.xcalendar.data.remoteDataSource.error.DataError
import com.debanshu.xcalendar.data.store.StoreException
import com.debanshu.xcalendar.domain.repository.RepositoryException

/**
 * Centralized error mapping utility for converting between error types.
 * 
 * This consolidates all error conversions in one place to:
 * - Ensure consistent user-facing error messages
 * - Simplify error handling in Use Cases
 * - Make error mapping testable
 * 
 * Error Hierarchy:
 * - DataError: Low-level network/local storage errors
 * - StoreException: Store5 operation failures
 * - RepositoryException: Repository-level failures
 * - DomainError: User-facing errors for UI
 */
object ErrorMapper {
    
    /**
     * Maps any throwable to a DomainError.
     * This is the main entry point for error conversion.
     */
    fun mapToDomainError(throwable: Throwable): DomainError {
        return when (throwable) {
            is RepositoryException -> mapRepositoryException(throwable)
            is StoreException -> mapStoreException(throwable)
            is EventValidationException -> DomainError.ValidationError(throwable.message ?: "Validation failed")
            else -> mapGenericException(throwable)
        }
    }
    
    /**
     * Maps a RepositoryException to DomainError.
     */
    private fun mapRepositoryException(exception: RepositoryException): DomainError {
        // Check if there's a cause that can give more specific error info
        val cause = exception.cause
        return when {
            cause is StoreException -> mapStoreException(cause)
            exception.message.contains("internet", ignoreCase = true) -> DomainError.NoInternet
            exception.message.contains("timeout", ignoreCase = true) -> DomainError.Timeout
            exception.message.contains("unauthorized", ignoreCase = true) -> DomainError.Unauthorized
            exception.message.contains("server", ignoreCase = true) -> DomainError.ServerError
            else -> DomainError.Unknown(exception.message)
        }
    }
    
    /**
     * Maps a StoreException to DomainError.
     */
    private fun mapStoreException(exception: StoreException): DomainError {
        val message = exception.message ?: "Store operation failed"
        return when {
            message.contains("NO_INTERNET", ignoreCase = true) -> DomainError.NoInternet
            message.contains("TIMEOUT", ignoreCase = true) -> DomainError.Timeout
            message.contains("UNAUTHORIZED", ignoreCase = true) -> DomainError.Unauthorized
            message.contains("SERVER", ignoreCase = true) -> DomainError.ServerError
            message.contains("not found", ignoreCase = true) -> DomainError.NotFound
            else -> DomainError.Unknown(message)
        }
    }
    
    /**
     * Maps generic exceptions to DomainError.
     */
    private fun mapGenericException(throwable: Throwable): DomainError {
        val message = throwable.message ?: "An unexpected error occurred"
        return when {
            message.contains("network", ignoreCase = true) -> DomainError.NoInternet
            message.contains("timeout", ignoreCase = true) -> DomainError.Timeout
            message.contains("database", ignoreCase = true) -> DomainError.DatabaseError
            else -> DomainError.Unknown(message)
        }
    }
}

/**
 * Extension function: Maps DataError to DomainError.
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

/**
 * Extension function: Maps Throwable to DomainError using ErrorMapper.
 */
fun Throwable.toDomainError(): DomainError = ErrorMapper.mapToDomainError(this)

/**
 * Extension function: Safely runs a block and returns DomainResult.
 * Automatically maps exceptions to DomainError.
 */
inline fun <T> runCatchingToDomainResult(block: () -> T): DomainResult<T> {
    return try {
        DomainResult.Success(block())
    } catch (e: Exception) {
        DomainResult.Error(e.toDomainError())
    }
}

/**
 * Extension function: Safely runs a suspend block and returns DomainResult.
 * Automatically maps exceptions to DomainError.
 */
suspend inline fun <T> runSuspendCatchingToDomainResult(
    crossinline block: suspend () -> T
): DomainResult<T> {
    return try {
        DomainResult.Success(block())
    } catch (e: Exception) {
        DomainResult.Error(e.toDomainError())
    }
}
