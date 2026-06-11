@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.domain.export

import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.Report

/** Builds interoperable export payloads (GeoJSON / CSV) from reports — UNDP must-have, on-device.
 *
 *  The property/column names mirror the BACKEND export schema (export_service.go), so an
 *  on-device file drops into the same tooling as a server export: damage_classification is the
 *  required 3-tier gate value {Minimal,Partial,Complete}; the raw grade, infrastructure name,
 *  Plus Code, GPS accuracy, the modular Appendix-1 answers and the description ride along. */
object ReportExporter {

    fun toGeoJson(reports: List<Report>): String {
        // Server-added modular keys are flattened DYNAMICALLY into extra properties, so a new
        // section appears in on-device exports automatically (same set on every feature).
        val extras = extraModularKeys(reports)
        val features = reports.joinToString(",") { r ->
            // Resolved reports carry a Point [lng,lat]; landmark-only (unresolved) reports get
            // geometry:null — we never emit a fabricated [0,0] point.
            val lat = r.location.lat
            val lng = r.location.lng
            val geom = if (r.location.locationResolved && lat != null && lng != null) {
                """{"type":"Point","coordinates":[$lng,$lat]}"""
            } else {
                "null"
            }
            """{"type":"Feature","geometry":$geom,"properties":{${properties(r, extras)}}}"""
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    private fun properties(r: Report, extras: List<String>): String = (
        listOf(
            "\"id\":\"${esc(r.id)}\"",
            "\"damage_classification\":\"${damageClassification(r)}\"",
            "\"damage\":\"${r.damage.name.lowercase()}\"",
            "\"possiblyDamaged\":${r.possiblyDamaged}",
            "\"infrastructure_type\":\"${esc(r.infraTypes.joinToString(";") { it.name.lowercase() })}\"",
            "\"infrastructure_name\":\"${esc(r.infraName.orEmpty())}\"",
            "\"hazard_type\":\"${esc(r.crisisNature.joinToString(";") { it.name.lowercase() })}\"",
            "\"timestamp\":\"${r.capturedAt}\"",
            "\"electricity\":\"${esc(r.modular?.electricity.orEmpty())}\"",
            "\"health_services\":\"${esc(r.modular?.healthServices.orEmpty())}\"",
            "\"pressing_needs\":\"${esc(pressingNeeds(r))}\"",
            "\"pressing_needs_other\":\"${esc(r.modular?.pressingNeedsOther.orEmpty())}\"",
            "\"debris\":\"${r.debris.name.lowercase()}\"",
            "\"buildingId\":\"${esc(r.buildingId.orEmpty())}\"",
            "\"place\":\"${esc(r.place)}\"",
            "\"plus_code\":\"${esc(r.location.plusCode.orEmpty())}\"",
            "\"accuracy_m\":${r.location.gpsAccuracyMeters?.toString() ?: "null"}",
            "\"description\":\"${esc(r.description?.original.orEmpty())}\"",
            "\"synced\":${r.isSynced}",
        ) + extras.map { key -> "\"${snake(key)}\":\"${esc(modularValue(r, key))}\"" }
        ).joinToString(",")

    fun toCsv(reports: List<Report>): String {
        // The three known sections keep their stable columns; server-added keys are appended
        // dynamically (snake_case, sorted) so a new section appears in exports automatically.
        val extras = extraModularKeys(reports)
        val header = "id,latitude,longitude,timestamp,damage_classification,damage,infrastructure_type," +
            "infrastructure_name,hazard_type,electricity,health_services,pressing_needs,pressing_needs_other," +
            "possiblyDamaged,debris,buildingId,place,plus_code,accuracy_m,description" +
            extras.joinToString("") { ",${snake(it)}" }
        val rows = reports.joinToString("\n") { r ->
            (
                listOf(
                    r.id,
                    // Blank coords for landmark-only (unresolved) reports — never a fabricated 0,0.
                    r.location.lat?.toString().orEmpty(),
                    r.location.lng?.toString().orEmpty(),
                    r.capturedAt.toString(),
                    damageClassification(r),
                    r.damage.name.lowercase(),
                    r.infraTypes.joinToString(";") { it.name.lowercase() },
                    r.infraName.orEmpty(),
                    r.crisisNature.joinToString(";") { it.name.lowercase() },
                    r.modular?.electricity.orEmpty(),
                    r.modular?.healthServices.orEmpty(),
                    pressingNeeds(r),
                    r.modular?.pressingNeedsOther.orEmpty(),
                    r.possiblyDamaged.toString(),
                    r.debris.name.lowercase(),
                    r.buildingId.orEmpty(),
                    r.place,
                    r.location.plusCode.orEmpty(),
                    r.location.gpsAccuracyMeters?.toString().orEmpty(),
                    r.description?.original.orEmpty(),
                ) + extras.map { modularValue(r, it) }
                ).joinToString(",") { csvCell(it) }
        }
        return "$header\n$rows"
    }

    /** Title-cased 3-tier gate value, matching the backend's damage_classification export field. */
    private fun damageClassification(r: Report): String = when (r.damage) {
        DamageTier.MINIMAL -> "Minimal"
        DamageTier.PARTIAL -> "Partial"
        DamageTier.COMPLETE -> "Complete"
    }

    private fun pressingNeeds(r: Report): String =
        r.modular?.pressingNeeds?.joinToString(";").orEmpty()

    // ── dynamic modular flattening (mirrors the backend's export_service behavior) ──

    /** Wire keys of the three built-in sections (and the free-text companion), which already
     *  have stable columns above — everything else is an "extra" flattened dynamically. */
    private val KNOWN_MODULAR_KEYS = setOf("electricity", "healthServices", "pressingNeeds", "pressingNeedsOther")

    /** Server-added modular wire keys present across [reports], sorted for a stable order. */
    private fun extraModularKeys(reports: List<Report>): List<String> =
        reports.flatMap { r ->
            val m = r.modular ?: return@flatMap emptyList<String>()
            m.single.keys + m.multi.keys + m.otherTexts.keys.map { "${it}Other" }
        }.distinct().filterNot { it in KNOWN_MODULAR_KEYS }.sorted()

    /** One report's flattened value for a modular wire [key] (multi-select joins with ";"). */
    private fun modularValue(r: Report, key: String): String {
        val m = r.modular ?: return ""
        return m.single[key]
            ?: m.multi[key]?.joinToString(";")
            ?: (if (key.endsWith("Other")) m.otherTexts[key.removeSuffix("Other")] else null)
            ?: ""
    }

    /** camelCase wire key → snake_case export column (healthServices → health_services). */
    private fun snake(key: String): String = buildString {
        key.forEach { c -> if (c.isUpperCase()) { append('_'); append(c.lowercaseChar()) } else append(c) }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun csvCell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"${s.replace("\"", "\"\"")}\"" else s
}
