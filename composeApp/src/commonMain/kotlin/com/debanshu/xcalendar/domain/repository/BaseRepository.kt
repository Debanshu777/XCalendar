package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.domain.util.DomainResult
import com.debanshu.xcalendar.domain.util.toDomainError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Base repository providing common patterns for error handling, logging, and coroutine context.
 * 
 * Benefits:
 * - Consistent error handling across all repositories
 * - Centralized logging for debugging
 * - Proper coroutine dispatcher management
 * - Reduces boilerplate in repository implementations
 * 
 * Usage:
 * ```kotlin
 * class MyRepository : BaseRepository() {
 *     suspend fun fetchData(): DomainResult<Data> = safeCall("fetchData") {
 *         api.getData()
 *     }
 * }
 * ```
 */
abstract class BaseRepository(
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Tag for logging - defaults to class name
     */
    protected open val tag: String get() = this::class.simpleName ?: "Repository"
    
    /**
     * Executes a suspend operation with error handling and logging.
     * 
     * @param operationName Name of the operation for logging
     * @param block The suspend function to execute
     * @return DomainResult with success data or error
     */
    protected suspend inline fun <T> safeCall(
        operationName: String,
        crossinline block: suspend () -> T
    ): DomainResult<T> {
        return withContext(ioDispatcher) {
            try {
                AppLogger.d { "$tag: Starting $operationName" }
                val result = block()
                AppLogger.d { "$tag: $operationName completed successfully" }
                DomainResult.Success(result)
            } catch (e: Exception) {
                AppLogger.e(e) { "$tag: $operationName failed" }
                DomainResult.Error(e.toDomainError())
            }
        }
    }
    
    /**
     * Executes a suspend operation, throwing RepositoryException on failure.
     * Use when you need to propagate errors up the call stack.
     * 
     * @param operationName Name of the operation for logging/error message
     * @param block The suspend function to execute
     * @return The result of the block
     * @throws RepositoryException on failure
     */
    protected suspend inline fun <T> safeCallOrThrow(
        operationName: String,
        crossinline block: suspend () -> T
    ): T {
        return withContext(ioDispatcher) {
            try {
                AppLogger.d { "$tag: Starting $operationName" }
                val result = block()
                AppLogger.d { "$tag: $operationName completed successfully" }
                result
            } catch (e: Exception) {
                val errorMessage = "$operationName failed: ${e.message}"
                AppLogger.e(e) { "$tag: $errorMessage" }
                throw RepositoryException(errorMessage, e)
            }
        }
    }
    
    /**
     * Wraps a Flow with error handling and proper dispatcher.
     * 
     * @param flowName Name of the flow for logging
     * @param defaultValue Value to emit on error
     * @param flow The flow to wrap
     * @return Flow with error handling
     */
    protected fun <T> safeFlow(
        flowName: String,
        defaultValue: T,
        flow: Flow<T>
    ): Flow<T> {
        return flow
            .catch { e ->
                AppLogger.e(e as? Exception ?: Exception(e)) { "$tag: $flowName error" }
                emit(defaultValue)
            }
            .flowOn(ioDispatcher)
    }
    
    /**
     * Logs a debug message with the repository tag.
     */
    protected fun logDebug(message: () -> String) {
        AppLogger.d { "$tag: ${message()}" }
    }
    
    /**
     * Logs an error with the repository tag.
     */
    protected fun logError(exception: Exception? = null, message: () -> String) {
        if (exception != null) {
            AppLogger.e(exception) { "$tag: ${message()}" }
        } else {
            AppLogger.e { "$tag: ${message()}" }
        }
    }
    
    /**
     * Logs an info message with the repository tag.
     */
    protected fun logInfo(message: () -> String) {
        AppLogger.i { "$tag: ${message()}" }
    }
}

/**
 * Extension function to convert a result to DomainResult.
 */
inline fun <T> Result<T>.toDomainResult(
    errorMapper: (Throwable) -> DomainError = { it.toDomainError() }
): DomainResult<T> {
    return fold(
        onSuccess = { DomainResult.Success(it) },
        onFailure = { DomainResult.Error(errorMapper(it)) }
    )
}

