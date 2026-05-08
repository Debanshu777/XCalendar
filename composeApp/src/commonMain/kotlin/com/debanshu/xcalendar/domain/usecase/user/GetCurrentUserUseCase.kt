package com.debanshu.xcalendar.domain.usecase.user

import org.koin.core.annotation.Factory

@Factory
class GetCurrentUserUseCase {
    companion object {
        private const val DEFAULT_USER_ID = "user_id"
    }

    operator fun invoke(): String = DEFAULT_USER_ID
}

