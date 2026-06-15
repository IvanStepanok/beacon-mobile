package com.stepanok.undp.core.storage

/** Tiny persistent key-value store (Android SharedPreferences / iOS NSUserDefaults). */
expect object Prefs {
    fun getBool(key: String, default: Boolean): Boolean
    fun setBool(key: String, value: Boolean)
    fun getString(key: String): String?
    fun setString(key: String, value: String)
}

object PrefKeys {
    const val ONBOARDING_SEEN = "onboarding_seen"

    /** Set once the user has been offered the "download your area for offline" prompt (shown the
     *  first time they recenter the map on themselves), so we never nag them again. */
    const val OFFLINE_PROMPT_SHOWN = "offline_prompt_shown"

    /** Per-install anonymous device identity (X-Device-Id). Generated once, persisted forever. */
    const val DEVICE_ID = "device_id"

    /** Last-fetched modular form schema (raw GET /form-schema JSON) — the offline fallback
     *  so the capture form renders without connectivity. Keyed PER-CRISIS by appending
     *  ":crisisId" (":default" when unscoped) so one crisis's overrides never leak into
     *  another. */
    const val FORM_SCHEMA = "form_schema"

    /** Last-known server-awarded points (granted once a report is verified) — shown while
     *  offline instead of a locally fabricated count. */
    const val PROFILE_POINTS = "profile_points"
}
