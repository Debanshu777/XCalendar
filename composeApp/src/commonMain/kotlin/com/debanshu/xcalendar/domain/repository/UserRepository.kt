package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.model.asUser
import com.debanshu.xcalendar.common.model.asUserEntity
import com.debanshu.xcalendar.data.localDataSource.UserDao
import com.debanshu.xcalendar.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Repository for the local user record.
 *
 * **Why no Store5**: the app is single-user and currently has no remote
 * user-profile API — `getUserFromApi` synthesises a dummy user and writes
 * it to the local `UserDao` (audit F21). Room is the source of truth. A
 * Store5 layer here would add coalescing/TTL/Bookkeeper machinery for a
 * code path that never touches the network. Revisit when a real auth
 * backend ships and user data needs cache-with-refresh semantics; until
 * then this stays a thin DAO wrapper.
 */
@Single(binds = [IUserRepository::class])
class UserRepository(
    private val userDao: UserDao,
) : BaseRepository(), IUserRepository {
    
    override suspend fun getUserFromApi() = safeCallOrThrow("getUserFromApi") {
        val dummyUser = User(
            id = "user_id",
            name = "Demo User",
            email = "user@example.com",
            photoUrl = "https://t4.ftcdn.net/jpg/00/04/09/63/360_F_4096398_nMeewldssGd7guDmvmEDXqPJUmkDWyqA.jpg",
        )
        addUser(dummyUser)
    }

    override fun getAllUsers(): Flow<List<User>> = 
        safeFlow(
            flowName = "getAllUsers",
            defaultValue = emptyList(),
            flow = userDao.getAllUsers().map { entities -> entities.map { it.asUser() } }
        )

    override suspend fun addUser(user: User) = safeCallOrThrow("addUser(${user.id})") {
        userDao.insertUser(user.asUserEntity())
    }

    override suspend fun deleteUser(user: User) = safeCallOrThrow("deleteUser(${user.id})") {
        userDao.deleteUser(user.asUserEntity())
    }
}
