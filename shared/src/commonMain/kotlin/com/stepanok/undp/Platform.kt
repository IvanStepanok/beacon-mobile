package com.stepanok.undp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform