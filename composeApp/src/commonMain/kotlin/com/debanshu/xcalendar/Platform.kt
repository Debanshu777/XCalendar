package com.debanshu.xcalendar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform