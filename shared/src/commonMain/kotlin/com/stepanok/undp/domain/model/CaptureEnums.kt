package com.stepanok.undp.domain.model

/** The challenge's REQUIRED 3-level damage classification — the only damage scale Beacon uses. */
enum class DamageTier { MINIMAL, PARTIAL, COMPLETE }

/** Required multi-select: type of affected infrastructure (7 categories + Other). */
enum class InfraType {
    RESIDENTIAL, COMMERCIAL, GOVERNMENT, UTILITY, TRANSPORT, COMMUNITY, PUBLIC, OTHER
}

/** Required multi-select: nature of the crisis. */
enum class CrisisNature {
    EARTHQUAKE, FLOOD, TSUNAMI, HURRICANE, WILDFIRE,   // natural
    EXPLOSION, CHEMICAL,                               // technological
    CONFLICT, CIVIL_UNREST                             // human-made
}

/** Required: is there debris that needs clearing near the site? */
enum class DebrisState { YES, NO, UNSURE }

// ── Modular Appendix-1 sections (optional) ──────────────────────────────────
// No longer hardcoded enums: the modular form is schema-driven (GET /form-schema → FormSchema.kt),
// so sections UNDP adds server-side render + submit without an app update. The wire vocabulary of
// the built-in sections lives in defaultFormSections(), mirroring the server's defaults.
