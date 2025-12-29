package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.domain.model.User
import kotlinx.coroutines.flow.Flow

interface IUserRepository {
    suspend fun getUserFromApi()

    fun getAllUsers(): Flow<List<User>>

    suspend fun addUser(user: User)

    suspend fun deleteUser(user: User)
}
