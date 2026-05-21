package com.debanshu.xcalendar.di

/**
 * Default session id — must align with
 * [com.debanshu.xcalendar.domain.usecase.user.GetCurrentUserUseCase] until
 * multi-login UI wires dynamic ids (Phase 9 / F23).
 */
const val DEFAULT_USER_SESSION_ID = "user_id"

/**
 * Scope source object for Koin's per-user Store5 graph (audit F23 / Phase 9).
 */
data class UserSession(val userId: String)
