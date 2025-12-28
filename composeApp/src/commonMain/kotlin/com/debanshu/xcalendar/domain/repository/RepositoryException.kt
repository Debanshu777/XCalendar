package com.debanshu.xcalendar.domain.repository

/**
 * Exception thrown when a repository operation fails.
 * This propagates errors from the data layer to the domain/UI layers
 * instead of silently swallowing them.
 */
class RepositoryException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

