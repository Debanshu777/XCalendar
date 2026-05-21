package com.debanshu.xcalendar.di

import org.koin.core.Koin
import org.koin.core.qualifier.TypeQualifier
import org.koin.core.scope.Scope

/**
 * Owns the active [UserSession] Koin [Scope] so ViewModels resolve Store5
 * repositories from the same per-user graph (audit F23 / Phase 9).
 *
 * On logout / account switch: call [closeCurrentSession] then
 * [openSessionForUser] so TTL trackers and in-memory stores are discarded.
 */
class UserSessionScopeHolder(
    private val koin: Koin,
) {
    private var activeScope: Scope = openScope(DEFAULT_USER_SESSION_ID)

    /** Scope used by `koinViewModel(..., scope = …)` at the app root. */
    val scope: Scope
        get() = activeScope

    private fun openScope(userId: String): Scope =
        koin.getScopeOrNull(userId)
            ?: koin.createScope(
                scopeId = userId,
                source = UserSession(userId),
                qualifier = TypeQualifier(UserSession::class),
            )

    fun closeCurrentSession() {
        runCatching {
            activeScope.close()
        }
    }

    /**
     * Closes the current session graph (stores, TTL trackers) and opens a new
     * one for [userId]. Call from auth when the signed-in user changes.
     */
    fun openSessionForUser(userId: String) {
        closeCurrentSession()
        activeScope = openScope(userId)
    }
}
