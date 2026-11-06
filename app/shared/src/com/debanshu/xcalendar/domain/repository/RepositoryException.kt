package com.debanshu.xcalendar.domain.repository

class RepositoryException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
