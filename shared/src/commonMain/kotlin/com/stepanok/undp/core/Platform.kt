package com.stepanok.undp.core

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Monotonic-ish wall clock, injectable so tests can pin time. */
interface AppClock {
    fun now(): Instant
}

@OptIn(ExperimentalTime::class)
class SystemClock : AppClock {
    override fun now(): Instant = Clock.System.now()
}

/** Generates idempotency keys / local ids. */
interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
