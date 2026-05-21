package com.debanshu.xcalendar.domain.usecase.user

import com.debanshu.xcalendar.di.DEFAULT_USER_SESSION_ID
import org.koin.core.annotation.Factory

@Factory
class GetCurrentUserUseCase {
    operator fun invoke(): String = DEFAULT_USER_SESSION_ID
}
