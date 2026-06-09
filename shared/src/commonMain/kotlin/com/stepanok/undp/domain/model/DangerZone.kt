package com.stepanok.undp.domain.model

/** Severity of a flagged hazardous area (operator-defined). */
enum class DangerSeverity { CAUTION, WARNING, CRITICAL }

/** An area reporters should avoid — surfaced in the Crisis & Safety tab. */
data class DangerZone(
    val id: String,
    val name: String,
    val note: String,
    val severity: DangerSeverity,
)
