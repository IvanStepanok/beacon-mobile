package com.stepanok.undp.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.designsystem.components.BeaconBottomSheet
import com.stepanok.undp.designsystem.components.BeaconButton
import com.stepanok.undp.designsystem.components.ChipSize
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.components.DamageChip
import com.stepanok.undp.designsystem.components.DamageStatsRow
import com.stepanok.undp.designsystem.components.PhotoPlaceholder
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.labels.damageLabel
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.designsystem.theme.beaconCardShadow
import com.stepanok.undp.domain.model.AreaGroup
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.feature.reportdetail.ReportDetailScreen
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.location.RequestLocationPermission
import com.stepanok.undp.map.BeaconMap
import com.stepanok.undp.map.GeoPoint
import com.stepanok.undp.map.MapDefaults
import com.stepanok.undp.map.rememberBeaconMapController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.crisis_none_nearby
import undp.shared.generated.resources.crisis_none_nearby_sub
import undp.shared.generated.resources.filter_all
import undp.shared.generated.resources.hotspots_reports
import undp.shared.generated.resources.hotspots_sub
import undp.shared.generated.resources.hotspots_title
import undp.shared.generated.resources.map_crisis_active
import undp.shared.generated.resources.map_filter_title
import undp.shared.generated.resources.map_offline_mode
import undp.shared.generated.resources.map_view_details
import undp.shared.generated.resources.map_reports_queued
import undp.shared.generated.resources.map_search_hint

object MapScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<MapScreenModel>()
        val state by model.state.collectAsState()
        val preview by model.selectedPreview.collectAsState()
        val filter by model.filter.collectAsState()
        val hotspots by model.hotspots.collectAsState()
        val colors = BeaconTheme.colors
        val nav = LocalNavigator.currentOrThrow
        val mapController = rememberBeaconMapController()
        val locationProvider = koinInject<LocationProvider>()
        val scope = rememberCoroutineScope()
        var showFilter by remember { mutableStateOf(false) }
        var showHotspots by remember { mutableStateOf(false) }
        // Ask for location permission once, then (re)resolve the user's location so the
        // map centres on THEM, not a hardcoded city.
        RequestLocationPermission { granted -> if (granted) model.resolveLocation() }

        // Centre on the user (instant last-known → refined) or a browsed crisis.
        LaunchedEffect(state.focusLat, state.focusLng, state.mode) {
            val lat = state.focusLat
            val lng = state.focusLng
            if (lat != null && lng != null) {
                val zoom = if (state.mode == MapMode.IN_CRISIS) MapDefaults.CITY_ZOOM else MapDefaults.NEIGHBORHOOD_ZOOM
                mapController.recenter(GeoPoint(lat, lng), zoom)
            }
        }

        val pins = remember(state.pins, filter) {
            filter?.let { f -> state.pins.filter { it.level == f } } ?: state.pins
        }

        Box(Modifier.fillMaxSize()) {
            BeaconMap(
                reports = pins,
                controller = mapController,
                onReportClick = { id -> model.selectReport(id) },
                center = MapDefaults.WORLD,
                zoom = MapDefaults.WORLD_ZOOM,
                modifier = Modifier.fillMaxSize(),
            )

            preview?.let { p ->
                ReportPreviewSheet(
                    preview = p,
                    onDismiss = { model.dismissPreview() },
                    onDetails = { nav.push(ReportDetailScreen(p.id)); model.dismissPreview() },
                )
            }

            if (showFilter) {
                FilterSheet(
                    current = filter,
                    onSelect = { model.setFilter(it); showFilter = false },
                    onDismiss = { showFilter = false },
                )
            }
            if (showHotspots) {
                HotspotsSheet(groups = hotspots, onDismiss = { showHotspots = false })
            }

            // Top overlays: search + filter, then crisis banner
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().safeTopPadding().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface.copy(alpha = 0.96f))
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(BeaconIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.map_search_hint), style = BeaconTheme.typography.bodyS, color = colors.ink3)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (filter != null) colors.primary else colors.surface.copy(alpha = 0.96f))
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                            .clickable { showFilter = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(BeaconIcons.Filter, contentDescription = "Filter", tint = if (filter != null) colors.onPrimary else colors.ink2, modifier = Modifier.size(20.dp))
                    }
                }

                if (state.mode == MapMode.IN_CRISIS && state.crisisTitle != null && !state.crisisDismissed) {
                    CrisisBanner(
                        title = state.crisisTitle!!,
                        subtitle = state.crisisSubtitle.orEmpty(),
                        onDismiss = { model.onIntent(MapIntent.DismissCrisis) },
                    )
                }
                if (state.mode == MapMode.NO_CRISIS) {
                    NoCrisisCard()
                }
            }

            // Bottom overlays: recenter, stats, offline indicator
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .beaconCardShadow(RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .clickable {
                            scope.launch {
                                // Instant: jump to the cached fix immediately (no 8s wait)…
                                locationProvider.lastKnown()?.let {
                                    mapController.recenter(GeoPoint(it.lat, it.lng), MapDefaults.NEIGHBORHOOD_ZOOM)
                                }
                                // …then refine with a fresh, precise fix when it arrives.
                                locationProvider.current()?.let {
                                    mapController.recenter(GeoPoint(it.lat, it.lng), MapDefaults.BUILDING_ZOOM)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BeaconIcons.Location, contentDescription = "Recenter", tint = colors.ink2, modifier = Modifier.size(20.dp))
                }

                DamageStatsRow(
                    counts = state.damageCounts,
                    modifier = Modifier.fillMaxWidth().beaconCardShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp)).clickable { showHotspots = true },
                )

                if (state.offline) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xF2281E46))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(BeaconIcons.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            "${stringResource(Res.string.map_offline_mode)} — ${stringResource(Res.string.map_reports_queued, state.queueCount)}",
                            style = BeaconTheme.typography.caption,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoCrisisCard() {
    val colors = BeaconTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.97f))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BeaconIcons.Location, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.crisis_none_nearby), style = BeaconTheme.typography.label, color = colors.ink)
            Text(
                stringResource(Res.string.crisis_none_nearby_sub),
                style = BeaconTheme.typography.caption, color = colors.ink2,
            )
        }
    }
}

@Composable
private fun CrisisBanner(title: String, subtitle: String, onDismiss: () -> Unit) {
    val colors = BeaconTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.completeSoft)
            .border(1.dp, colors.complete.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.complete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BeaconIcons.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${stringResource(Res.string.map_crisis_active)} · $title",
                style = BeaconTheme.typography.label,
                color = colors.complete,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = BeaconTheme.typography.caption, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Icon(BeaconIcons.Close, contentDescription = "Dismiss", tint = colors.ink3, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(0.dp))
    }
}

@Composable
private fun ReportPreviewSheet(preview: ReportPreview, onDismiss: () -> Unit, onDetails: () -> Unit) {
    val colors = BeaconTheme.colors
    BeaconBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) {
                    PhotoPlaceholder(modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // weight(fill=false): an unweighted full-UUID id would starve the chip
                        // to 0dp width (its label then stacks one char per line).
                        Text(
                            "#${preview.id}", style = BeaconTheme.typography.titleS, color = colors.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        DamageChip(tier = preview.damage, label = damageLabel(preview.damage), size = ChipSize.Sm)
                    }
                    Text("${preview.time} · ${preview.place}", style = BeaconTheme.typography.caption, color = colors.ink3)
                    if (preview.plusCode.isNotEmpty() && preview.plusCode != "garden.tribe.sparkle") {
                        Text(preview.plusCode, style = BeaconTheme.typography.mono, color = colors.ink2)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            BeaconButton(
                text = stringResource(Res.string.map_view_details),
                onClick = onDetails,
                fullWidth = true,
                trailingIcon = BeaconIcons.ChevronRight,
            )
        }
    }
}

@Composable
private fun FilterSheet(current: DamageTier?, onSelect: (DamageTier?) -> Unit, onDismiss: () -> Unit) {
    val colors = BeaconTheme.colors
    BeaconBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(Res.string.map_filter_title), style = BeaconTheme.typography.titleM, color = colors.ink)
            Spacer(Modifier.height(8.dp))
            FilterRow(stringResource(Res.string.filter_all), null, current, onSelect)
            DamageTier.entries.forEach { tier ->
                FilterRow(damageLabel(tier), tier, current, onSelect)
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, level: DamageTier?, current: DamageTier?, onSelect: (DamageTier?) -> Unit) {
    val colors = BeaconTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(level) }.padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (level != null) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.damageColor(level)))
            Text(label, style = BeaconTheme.typography.titleS, color = colors.ink)
        }
        if (current == level) Icon(BeaconIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HotspotsSheet(groups: List<AreaGroup>, onDismiss: () -> Unit) {
    val colors = BeaconTheme.colors
    BeaconBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(Res.string.hotspots_title), style = BeaconTheme.typography.titleM, color = colors.ink)
            Text(stringResource(Res.string.hotspots_sub), style = BeaconTheme.typography.bodyS, color = colors.ink2)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.damageColor(g.worst)))
                        Column(Modifier.weight(1f)) {
                            Text(g.area, style = BeaconTheme.typography.titleS, color = colors.ink)
                            Text(stringResource(Res.string.hotspots_reports, g.count), style = BeaconTheme.typography.caption, color = colors.ink3)
                        }
                        DamageChip(tier = g.worst, label = damageLabel(g.worst), size = ChipSize.Sm)
                    }
                }
            }
        }
    }
}
