@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp

import com.stepanok.undp.data.remote.toSubmitDto
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * B2 contract guard: the advisory on-device classifier suggestion (aiLevel/aiConfidence) maps to
 * the wire ALONGSIDE the independent human grade, and is omitted entirely when the model abstained.
 */
class DamageSuggestionMappingTest {

    private fun report(ai: DamageTier?, confidence: Int?) = Report(
        id = "r",
        idempotencyKey = "k",
        damage = DamageTier.COMPLETE, // the human grade — always authoritative + independent
        aiLevel = ai,
        aiConfidence = confidence,
        location = ReportLocation(lat = 1.0, lng = 2.0),
        capturedAt = Instant.fromEpochMilliseconds(0L),
    )

    @Test
    fun advisorySuggestionMapsToWire() {
        val dto = report(DamageTier.PARTIAL, 78).toSubmitDto()
        assertEquals("partial", dto.aiLevel)
        assertEquals(78, dto.aiConfidence)
        assertEquals("complete", dto.damage) // human grade unchanged by the AI suggestion
    }

    @Test
    fun abstainOmitsAiFields() {
        val dto = report(ai = null, confidence = null).toSubmitDto()
        assertNull(dto.aiLevel)
        assertNull(dto.aiConfidence)
    }
}
