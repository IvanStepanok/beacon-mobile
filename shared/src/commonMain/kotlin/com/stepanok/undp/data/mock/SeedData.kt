@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.mock

import com.stepanok.undp.domain.model.Badge
import com.stepanok.undp.domain.model.Crisis
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.PhotoRef
import com.stepanok.undp.domain.model.Profile
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Mock seed for the Antakya earthquake scenario (mirrors the prototype's 12 reports). */
object SeedData {
    const val ANTAKYA_LAT = 36.2021
    const val ANTAKYA_LNG = 36.1601

    fun crisis(now: Instant): Crisis = Crisis(
        id = "crisis-antakya",
        title = "Earthquake M 6.4",
        area = "Antakya district",
        nature = CrisisNature.EARTHQUAKE,
        startedAt = now - 180.minutes,
        centerLat = ANTAKYA_LAT,
        centerLng = ANTAKYA_LNG,
    )

    private data class Seed(
        val id: String,
        val damage: DamageLevel,
        val minutesAgo: Int,
        val place: String,
        val synced: Boolean,
        val sizeBytes: Long,
        val debris: DebrisState,
        val buildingId: String,
        val lat: Double,
        val lng: Double,
        val mine: Boolean = false,
        val lifeSafety: Boolean = false,
        val possiblyDamaged: Boolean = false,
    )

    private val seeds = listOf(
        Seed("1203", DamageLevel.SEVERE, 2, "Akdeniz Ave", false, 2_400_000, DebrisState.YES, "b-47", 36.2025, 36.1605, mine = true, lifeSafety = true),
        Seed("1204", DamageLevel.SLIGHT, 14, "Bahçe Sk.", false, 1_800_000, DebrisState.NO, "b-12", 36.2010, 36.1630, mine = true),
        Seed("1205", DamageLevel.DESTROYED, 38, "Saray Cd.", false, 3_200_000, DebrisState.YES, "b-88", 36.2040, 36.1580, mine = true, lifeSafety = true),
        Seed("1206", DamageLevel.MODERATE, 60, "İstiklal Sk.", false, 2_100_000, DebrisState.NO, "b-33", 36.1990, 36.1610, mine = true),
        Seed("1201", DamageLevel.SLIGHT, 120, "Mevlana Mh.", true, 2_700_000, DebrisState.NO, "b-47", 36.2025, 36.1605, mine = true),
        Seed("1200", DamageLevel.DESTROYED, 130, "Pazar Sk.", true, 3_400_000, DebrisState.YES, "b-90", 36.2055, 36.1560),
        Seed("1199", DamageLevel.NONE, 180, "Kale Mh.", true, 1_500_000, DebrisState.NO, "b-47", 36.2025, 36.1605, mine = true),
        Seed("1198", DamageLevel.MODERATE, 200, "Liman Sk.", true, 2_200_000, DebrisState.NO, "b-21", 36.2000, 36.1595),
        Seed("1197", DamageLevel.SLIGHT, 240, "Çiçek Cd.", true, 1_600_000, DebrisState.NO, "b-15", 36.2060, 36.1640),
        Seed("1196", DamageLevel.SEVERE, 300, "Demir Mh.", true, 3_000_000, DebrisState.YES, "b-77", 36.2030, 36.1660),
        Seed("1195", DamageLevel.NONE, 320, "Kuş Sk.", true, 1_400_000, DebrisState.NO, "b-09", 36.1980, 36.1585, possiblyDamaged = true),
        Seed("1194", DamageLevel.MODERATE, 360, "Gül Cd.", true, 2_500_000, DebrisState.NO, "b-55", 36.2045, 36.1620),
    )

    fun reports(now: Instant): List<Report> = seeds.map { s ->
        Report(
            id = s.id,
            idempotencyKey = "idem-${s.id}",
            photos = listOf(PhotoRef(localPath = "mock://${s.id}.jpg", sizeBytes = s.sizeBytes)),
            damage = s.damage,
            possiblyDamaged = s.possiblyDamaged,
            lifeSafety = s.lifeSafety,
            infraTypes = setOf(InfraType.RESIDENTIAL),
            crisisNature = setOf(CrisisNature.EARTHQUAKE),
            debris = s.debris,
            location = ReportLocation(
                lat = s.lat, lng = s.lng, buildingId = s.buildingId,
                what3words = "garden.tribe.sparkle", gpsAccuracyMeters = 8.0,
            ),
            description = ReportDescription(
                original = "Cephe çatlakları görünüyor, çatı sağlam.",
                originalLang = "tr",
                translated = "Facade cracks visible, roof intact.",
                translatedLang = "en",
            ),
            capturedAt = now - s.minutesAgo.minutes,
            buildingId = s.buildingId,
            sync = if (s.synced) SyncState.Synced else SyncState.Queued,
            place = s.place,
            isMine = s.mine,
        )
    }

    fun profile(): Profile = Profile(
        anonymousId = "A4-92K",
        reportCount = 12,
        buildingCount = 8,
        points = 120,
        badges = listOf(
            Badge("first-responder", "First responder", earned = true),
            Badge("map-maker", "Map maker", earned = true),
            Badge("hero-50", "Hero × 50", earned = false, progressLabel = "12/50"),
        ),
    )
}
