@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.domain.export

import com.stepanok.undp.domain.model.Report

/** Builds interoperable export payloads (GeoJSON / CSV) from reports — UNDP must-have, on-device. */
object ReportExporter {

    fun toGeoJson(reports: List<Report>): String {
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
            """{"type":"Feature","geometry":$geom,"properties":{${properties(r)}}}"""
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    private fun properties(r: Report): String = listOf(
        "\"id\":\"${esc(r.id)}\"",
        "\"damage\":\"${r.damage.name.lowercase()}\"",
        "\"infrastructure\":\"${esc(r.infraTypes.joinToString(";") { it.name.lowercase() })}\"",
        "\"crisis\":\"${esc(r.crisisNature.joinToString(";") { it.name.lowercase() })}\"",
        "\"debris\":\"${r.debris.name.lowercase()}\"",
        "\"buildingId\":\"${esc(r.buildingId.orEmpty())}\"",
        "\"capturedAt\":\"${r.capturedAt}\"",
        "\"place\":\"${esc(r.place)}\"",
        "\"synced\":${r.isSynced}",
    ).joinToString(",")

    fun toCsv(reports: List<Report>): String {
        val header = "id,damage,lat,lng,infrastructure,crisis,debris,buildingId,capturedAt,place"
        val rows = reports.joinToString("\n") { r ->
            listOf(
                r.id,
                r.damage.name.lowercase(),
                // Blank coords for landmark-only (unresolved) reports — never a fabricated 0,0.
                r.location.lat?.toString().orEmpty(),
                r.location.lng?.toString().orEmpty(),
                r.infraTypes.joinToString(";") { it.name.lowercase() },
                r.crisisNature.joinToString(";") { it.name.lowercase() },
                r.debris.name.lowercase(),
                r.buildingId.orEmpty(),
                r.capturedAt.toString(),
                r.place,
            ).joinToString(",") { csvCell(it) }
        }
        return "$header\n$rows"
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun csvCell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"${s.replace("\"", "\"\"")}\"" else s
}
