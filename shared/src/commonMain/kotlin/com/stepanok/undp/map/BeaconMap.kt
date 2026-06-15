package com.stepanok.undp.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.theme.BeaconTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.getBaseSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.MultiPolygon
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position

/** Imperative handle so screens can recenter the map without touching MapLibre types directly. */
class BeaconMapController {
    internal val recenterRequest = MutableStateFlow<Pair<GeoPoint, Double>?>(null)
    fun recenter(point: GeoPoint, zoom: Double = MapDefaults.CITY_ZOOM) {
        recenterRequest.value = point to zoom
    }

    /** The map's live centre, updated as the user pans/zooms. Lets a screen offer "download the
     *  area I'm looking at" instead of being tied to the (often-wrong on emulators) GPS location. */
    val currentCenter = MutableStateFlow<GeoPoint?>(null)
}

@Composable
fun rememberBeaconMapController(): BeaconMapController = remember { BeaconMapController() }

/**
 * The only map composable the app sees — MapLibre Native under the hood, behind plain data models.
 * Renders the OpenFreeMap basemap with clustered, 3-tier-colored damage pins.
 */
@Composable
fun BeaconMap(
    reports: List<ReportPin>,
    modifier: Modifier = Modifier,
    controller: BeaconMapController = rememberBeaconMapController(),
    center: GeoPoint = MapDefaults.ANTAKYA,
    zoom: Double = MapDefaults.CITY_ZOOM,
    styleUri: String = MapDefaults.OPEN_FREE_MAP_LIBERTY,
    footprints: Boolean = false,
    /** Optional "photo was taken here" hint (from a picked photo's EXIF GPS). Drawn as a distinct
     *  hollow ring + dot — a suggestion only; it is NOT a report pin and not the chosen location. */
    photoHint: GeoPoint? = null,
    onMapTap: ((GeoPoint) -> Unit)? = null,
    onFootprintTap: ((GeoPoint, String) -> Unit)? = null,
    onReportClick: ((String) -> Unit)? = null,
) {
    val colors = BeaconTheme.colors
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(target = Position(center.lng, center.lat), zoom = zoom),
    )

    LaunchedEffect(controller) {
        controller.recenterRequest.collect { request ->
            if (request != null) {
                val (point, z) = request
                cameraState.animateTo(CameraPosition(target = Position(point.lng, point.lat), zoom = z))
                controller.recenterRequest.value = null
            }
        }
    }

    // Publish the live camera centre so screens can offer "download what I'm viewing".
    LaunchedEffect(cameraState, controller) {
        snapshotFlow { cameraState.position.target }
            .collect { target -> controller.currentCenter.value = GeoPoint(target.latitude, target.longitude) }
    }

    val featuresJson = remember(reports) { reports.toGeoJson() }
    // Footprint highlight is hoisted here (not inside the `footprints` block) so a tap
    // on EMPTY map — handled in onMapClick below — can clear it. Previously the
    // highlight was set on a footprint tap and never reset, so once you picked a
    // building the selection visually "stuck" and could not be moved or removed.
    var selectedFootprint by remember { mutableStateOf<Geometry?>(null) }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri(styleUri),
        cameraState = cameraState,
        onMapClick = { position, _ ->
            // Empty-area tap: drop the building highlight (the screen model also clears
            // buildingId via onMapTap) and pin the free point the user tapped instead.
            selectedFootprint = null
            onMapTap?.invoke(GeoPoint(position.latitude, position.longitude))
            ClickResult.Pass
        },
    ) {
        // Real building footprints from the basemap's OpenMapTiles "building" source-layer.
        if (footprints) {
            getBaseSource("openmaptiles")?.let { tiles ->
                FillLayer(
                    id = "beacon-footprints",
                    source = tiles,
                    sourceLayer = "building",
                    color = const(colors.primary),
                    opacity = const(0.18f),
                    onClick = { features ->
                        val tapped = features.firstOrNull()
                        val geom = tapped?.geometry
                        val centroid = geom?.let { centroidOf(it) }
                        if (geom != null && centroid != null) {
                            selectedFootprint = geom
                            val id = stableFootprintId(tapped, geom)
                            if (onFootprintTap != null) onFootprintTap(centroid, id) else onMapTap?.invoke(centroid)
                            ClickResult.Consume
                        } else {
                            ClickResult.Pass
                        }
                    },
                )
                LineLayer(
                    id = "beacon-footprints-outline",
                    source = tiles,
                    sourceLayer = "building",
                    color = const(colors.primary),
                    width = const(0.8.dp),
                )
            }
            // Highlight the tapped building.
            val selectedFeatures = remember(selectedFootprint) {
                FeatureCollection(listOfNotNull(selectedFootprint?.let { Feature(geometry = it, properties = null) }))
            }
            val selectedSource = rememberGeoJsonSource(GeoJsonData.Features(selectedFeatures))
            FillLayer(
                id = "beacon-footprint-selected",
                source = selectedSource,
                color = const(colors.primary),
                opacity = const(0.55f),
            )
        }
        val source = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(featuresJson),
            options = GeoJsonOptions(cluster = true, clusterRadius = 50, clusterMaxZoom = 14),
        )

        CircleLayer(
            id = "report-clusters",
            source = source,
            filter = feature.has("point_count"),
            color = const(colors.primary),
            opacity = const(0.92f),
            radius = step(
                input = feature["point_count"].asNumber(),
                fallback = const(16.dp),
                10 to const(20.dp),
                50 to const(26.dp),
                200 to const(34.dp),
            ),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            onClick = { clicked ->
                val pt = clicked.firstOrNull()?.geometry as? Point
                if (pt != null) {
                    scope.launch {
                        cameraState.animateTo(CameraPosition(target = pt.coordinates, zoom = cameraState.position.zoom + 2.0))
                    }
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            },
        )
        SymbolLayer(
            id = "report-cluster-count",
            source = source,
            filter = feature.has("point_count"),
            textField = feature["point_count_abbreviated"].asString(),
            textFont = const(listOf("Noto Sans Regular")),
            textColor = const(Color.White),
        )
        CircleLayer(
            id = "report-pins",
            source = source,
            filter = !feature.has("point_count"),
            color = switch(
                feature["level"].asString(),
                case(label = "minimal", output = const(colors.ok)),
                case(label = "partial", output = const(colors.warn)),
                fallback = const(colors.complete),
            ),
            radius = const(7.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.5.dp),
            onClick = { clicked ->
                val id = clicked.firstOrNull()?.getStringProperty("id")
                if (id != null && onReportClick != null) {
                    onReportClick(id)
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            },
        )

        // "Photo was taken here" hint, from a picked photo's EXIF GPS. A hollow ring + small dot —
        // visually distinct from report pins and from the centre crosshair, so it reads as a
        // suggestion, not the chosen location. Pure CircleLayers (no glyph/sprite) so it renders
        // offline. Hoisted last → drawn on top of the pins.
        if (photoHint != null) {
            val hintJson = remember(photoHint) {
                """{"type":"FeatureCollection","features":[{"type":"Feature",""" +
                    """"geometry":{"type":"Point","coordinates":[${photoHint.lng},${photoHint.lat}]},"properties":{}}]}"""
            }
            val hintSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(hintJson))
            CircleLayer(
                id = "photo-hint-halo",
                source = hintSource,
                color = const(Color.Transparent),
                radius = const(13.dp),
                strokeColor = const(colors.primary),
                strokeWidth = const(2.dp),
                opacity = const(0.95f),
            )
            CircleLayer(
                id = "photo-hint-dot",
                source = hintSource,
                color = const(colors.primary),
                radius = const(4.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(1.5.dp),
            )
        }
    }
}

/** Build a GeoJSON FeatureCollection string directly — avoids coupling to the spatialk type API. */
private fun List<ReportPin>.toGeoJson(): String {
    val features = joinToString(",") { p ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.lng},${p.lat}]},""" +
            """"properties":{"id":"${p.id}","level":"${p.level.name.lowercase()}"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/** Rough centroid of a tapped building polygon (outer ring average) for snapping the report. */
private fun centroidOf(geom: Geometry): GeoPoint? {
    val ring = outerRing(geom)?.takeIf { it.isNotEmpty() } ?: return null
    return GeoPoint(ring.map { it.latitude }.average(), ring.map { it.longitude }.average())
}

/** Stable identity for a tapped building footprint. Prefers a feature id promoted by the
 *  source-layer; otherwise an FNV-1a hash of the polygon's normalized outer ring, so the
 *  same building yields the same id across taps regardless of where inside it the user taps. */
private fun stableFootprintId(feature: Feature<*, *>, geom: Geometry): String {
    feature.id?.content?.takeIf { it.isNotBlank() }?.let { return "fp-$it" }
    val ring = outerRing(geom)
    if (ring.isNullOrEmpty()) return "fp-0"
    return "fp-" + fnv1a(normalizedRing(ring))
}

/** Outer ring of a Polygon, or the outer ring of the first ring-group of a MultiPolygon. */
private fun outerRing(geom: Geometry): List<Position>? = when (geom) {
    is Polygon -> geom.coordinates.firstOrNull()
    is MultiPolygon -> geom.coordinates.firstOrNull()?.firstOrNull()
    else -> null
}

/** Round to ~1e-6 deg (~0.1 m) and drop a duplicated closing vertex so jitter/closure can't shift the id. */
private fun normalizedRing(ring: List<Position>): String {
    fun r(v: Double): Long = kotlin.math.round(v * 1_000_000.0).toLong()
    val pts = if (ring.size > 1 && r(ring.first().longitude) == r(ring.last().longitude) && r(ring.first().latitude) == r(ring.last().latitude)) ring.dropLast(1) else ring
    return pts.joinToString(";") { "${r(it.longitude)},${r(it.latitude)}" }
}

/** Deterministic 32-bit FNV-1a as an 8-char hex string (KMP-safe, no platform hashing). */
private fun fnv1a(s: String): String {
    var h = 0x811c9dc5.toInt()
    for (c in s) { h = h xor c.code; h *= 0x01000193 }
    return (h.toLong() and 0xffffffffL).toString(16).padStart(8, '0')
}
