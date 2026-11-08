package com.debanshu.xcalendar.common.model

import com.debanshu.xcalendar.data.localDataSource.model.UserEntity
import com.debanshu.xcalendar.domain.model.User

fun UserEntity.asUser(): User =
    User(id, name, email, photoUrl)

fun User.asUserEntity(): UserEntity =
    UserEntity(id, name, email, photoUrl)