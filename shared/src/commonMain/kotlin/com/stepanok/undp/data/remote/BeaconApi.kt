package com.stepanok.undp.data.remote

import com.stepanok.undp.core.network.DeviceId
import com.stepanok.undp.core.network.beaconBaseUrl
import com.stepanok.undp.core.network.beaconHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Non-2xx reply from the backend: the HTTP status plus the parsed {error, message} envelope
 * (or the raw body when the envelope doesn't parse). Lets the sync layer split TERMINAL
 * rejections (4xx the server will never accept) from retryable failures (5xx/408/429/network).
 */
class BeaconApiException(
    val status: Int,
    /** Server envelope code, e.g. "duplicate" | "validation" | "rate_limited"; "" if unparsed. */
    val errorCode: String,
    /** Server envelope message, falling back to the raw response body. */
    val serverMessage: String,
) : Exception("HTTP $status ${errorCode.ifBlank { "error" }}: $serverMessage") {
    /** Permanently rejected: any 4xx except 408 (timeout) and 429 (rate-limit), which are
     *  transient by definition. Retrying a terminal payload can never succeed. */
    val isTerminalRejection: Boolean get() = status in 400..499 && status != 408 && status != 429
}

/** Lenient envelope parser — a non-JSON body (proxy error page, truncation) must never throw. */
private val errorEnvelopeJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun apiException(status: Int, body: String): BeaconApiException {
    val envelope = runCatching { errorEnvelopeJson.decodeFromString<ErrorEnvelopeDto>(body) }.getOrNull()
    return BeaconApiException(
        status = status,
        errorCode = envelope?.error.orEmpty(),
        serverMessage = envelope?.message?.ifBlank { null } ?: body,
    )
}

/** Builds the platform HTTP client with JSON negotiation + the anonymous device header. */
fun beaconHttpClient(): HttpClient = HttpClient(beaconHttpEngine()) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
    }
    // Resolved per request (not captured at construction) so the persisted per-install id is
    // read from storage after app init, and stays correct for the life of the client.
    defaultRequest { header("X-Device-Id", DeviceId.get()) }
}

/** Thin typed wrapper over the Beacon backend's public (anonymous) endpoints. */
class BeaconApi(
    private val client: HttpClient = beaconHttpClient(),
    base: String = beaconBaseUrl,
) {
    private val v1 = "$base/api/v1"

    /** Map pins scoped by crisis and/or viewport bbox ("minLng,minLat,maxLng,maxLat").
     *  At least one must be non-null (a user anywhere loads only what's near them). */
    suspend fun latestPerBuilding(crisisId: String? = null, bbox: String? = null): List<ReportDto> =
        client.get("$v1/reports/latest-per-building") {
            crisisId?.let { parameter("crisisId", it) }
            bbox?.let { parameter("bbox", it) }
        }.body<ItemsDto<ReportDto>>().items

    suspend fun areaGroups(crisisId: String): List<AreaGroupDto> =
        client.get("$v1/reports/area-groups") { parameter("crisisId", crisisId) }
            .body<ItemsDto<AreaGroupDto>>().items

    /** Active/proposed crises near a point (location-first launch). */
    suspend fun crisesNear(lat: Double, lng: Double, radiusKm: Double = 50.0): List<CrisisDto> =
        client.get("$v1/crises/near") {
            parameter("lat", lat); parameter("lng", lng); parameter("radiusKm", radiusKm)
        }.body<ItemsDto<CrisisDto>>().items

    /** All crises with the given status (e.g. "active") — for the "browse crises" list. */
    suspend fun crises(status: String = "active"): List<CrisisDto> =
        client.get("$v1/crises") { parameter("status", status) }
            .body<ItemsDto<CrisisDto>>().items

    suspend fun report(id: String): ReportDto? {
        val r = client.get("$v1/reports/$id")
        return if (r.status.isSuccess()) r.body() else null
    }

    suspend fun buildingTimeline(buildingId: String): BuildingTimelineDto? {
        val r = client.get("$v1/buildings/$buildingId/timeline")
        return if (r.status.isSuccess()) r.body() else null
    }

    suspend fun activeCrisis(): CrisisDto? {
        val r = client.get("$v1/crises/active")
        return if (r.status == HttpStatusCode.OK) r.body() else null
    }

    suspend fun dangerZones(crisisId: String): List<DangerZoneDto> =
        client.get("$v1/crises/$crisisId/danger-zones").body<ItemsDto<DangerZoneDto>>().items

    suspend fun profile(): ProfileDto = client.get("$v1/profile").body()

    /** Global client config — the capture scale (tier3 | ems98). */
    suspend fun config(): ConfigDto = client.get("$v1/config").body()

    /** The modular capture-form definition (PUBLIC, like /crises): the built-in Appendix-1
     *  sections with [crisisId]'s required/disabled overrides applied; with no crisisId the
     *  server scopes to the newest active crisis. */
    suspend fun formSchema(crisisId: String? = null): FormSchemaDto {
        val r = client.get("$v1/form-schema") { crisisId?.let { parameter("crisisId", it) } }
        // A non-2xx error envelope would decode into an EMPTY schema (ignoreUnknownKeys) and
        // silently hide every section — throw so the caller falls back to cache/defaults.
        if (!r.status.isSuccess()) error("form-schema failed: ${r.status}")
        return r.body()
    }

    suspend fun submit(req: SubmitReportDto) {
        val response = client.post("$v1/reports") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        // expectSuccess = false, so a 4xx/5xx is NOT thrown by Ktor: a swallowed error here would
        // let the report be marked Synced. Throw a TYPED exception carrying the status + the
        // server's {error, message} envelope so RemoteReportRepository.uploadOne can park a
        // terminal 4xx (409 duplicate / 400 validation) as Rejected instead of retrying forever.
        if (!response.status.isSuccess()) {
            throw apiException(response.status.value, response.bodyAsText())
        }
    }

    /** Uploads the captured photo bytes for a report (multipart "file"). */
    suspend fun uploadPhoto(reportId: String, bytes: ByteArray) {
        val response = client.post("$v1/reports/$reportId/photo") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                            },
                        )
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            error("photo upload failed: ${response.status} ${response.bodyAsText()}")
        }
    }
}
