package com.stepanok.undp.domain.model

/** 5-level ordinal damage grade aligned to EMS-98 / Copernicus EMS / UNOSAT. */
enum class DamageLevel { NONE, SLIGHT, MODERATE, SEVERE, DESTROYED }

/** The challenge's REQUIRED 3-level core indicator. EMS-98 grades roll up to this. */
enum class DamageTier { MINIMAL, PARTIAL, COMPLETE }

/** Roll a 5-level grade up to the required 3 tiers. */
fun DamageLevel.toTier(): DamageTier = when (this) {
    DamageLevel.NONE, DamageLevel.SLIGHT -> DamageTier.MINIMAL
    DamageLevel.MODERATE, DamageLevel.SEVERE -> DamageTier.PARTIAL
    DamageLevel.DESTROYED -> DamageTier.COMPLETE
}

/** A representative 5-level grade for a tier — lets the existing DamageLevel-based
 *  display (colors/labels/pins) render a tier3 report sensibly (green/amber/terracotta). */
fun DamageTier.representativeLevel(): DamageLevel = when (this) {
    DamageTier.MINIMAL -> DamageLevel.NONE
    DamageTier.PARTIAL -> DamageLevel.MODERATE
    DamageTier.COMPLETE -> DamageLevel.DESTROYED
}

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
