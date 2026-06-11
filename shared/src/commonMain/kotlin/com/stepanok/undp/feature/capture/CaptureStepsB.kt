package com.stepanok.undp.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.labels.crisisLabel
import com.stepanok.undp.designsystem.labels.damageLabel
import com.stepanok.undp.designsystem.labels.debrisLabel
import com.stepanok.undp.designsystem.labels.infraLabel
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.core.location.RequestLocationPermission
import com.stepanok.undp.core.media.CapturedImage
import com.stepanok.undp.map.BeaconMap
import com.stepanok.undp.map.GeoPoint
import com.stepanok.undp.map.MapDefaults
import com.stepanok.undp.map.rememberBeaconMapController
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.capture_anon_badge
import undp.shared.generated.resources.capture_building_selected
import undp.shared.generated.resources.capture_describe_hint
import undp.shared.generated.resources.capture_describe_q
import undp.shared.generated.resources.capture_describe_sub
import undp.shared.generated.resources.capture_dup_warning
import undp.shared.generated.resources.capture_locating
import undp.shared.generated.resources.capture_locating_you
import undp.shared.generated.resources.capture_location_confirm
import undp.shared.generated.resources.capture_location_q
import undp.shared.generated.resources.capture_location_required
import undp.shared.generated.resources.capture_location_set
import undp.shared.generated.resources.capture_location_sub
import undp.shared.generated.resources.capture_landmark_hint
import undp.shared.generated.resources.capture_photo_label
import undp.shared.generated.resources.capture_privacy
import undp.shared.generated.resources.capture_review_q
import undp.shared.generated.resources.capture_review_sub
import undp.shared.generated.resources.capture_skip_desc
import undp.shared.generated.resources.capture_submit
import undp.shared.generated.resources.capture_w3w_label
import undp.shared.generated.resources.review_row_crisis
import undp.shared.generated.resources.review_row_damage
import undp.shared.generated.resources.review_row_debris
import undp.shared.generated.resources.review_row_description
import undp.shared.generated.resources.review_row_location
import undp.shared.generated.resources.review_row_type
import undp.shared.generated.resources.step_continue

// ── Location ──────────────────────────────────────────────────────────────────

@Composable
fun LocationStep(
    draft: CaptureDraft,
    duplicateWarning: String?,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    // Once foreground-location permission is granted, pull the real device fix into the draft.
    RequestLocationPermission { granted -> if (granted) onIntent(CaptureIntent.RequestDeviceLocation) }
    val mapController = rememberBeaconMapController()
    val lat = draft.lat
    val lng = draft.lng
    val fix = if (lat != null && lng != null) GeoPoint(lat, lng) else null
    // A report must carry a REAL location: a GPS fix / tapped footprint, or a non-blank landmark.
    // Block Continue until one of those exists so we never submit a fabricated point.
    val hasLocation = fix != null || draft.landmark.isNotBlank()
    // Recenter the map on the real GPS fix as soon as it arrives.
    LaunchedEffect(fix) {
        if (fix != null) mapController.recenter(fix, MapDefaults.BUILDING_ZOOM)
    }
    StepShell(current, total, onBack, onContinue, canContinue = hasLocation, continueLabel = stringResource(Res.string.capture_location_confirm)) {
        StepHeading(stringResource(Res.string.capture_location_q), stringResource(Res.string.capture_location_sub))
        Box(
            Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            BeaconMap(
                reports = emptyList(),
                controller = mapController,
                center = fix ?: MapDefaults.WORLD,
                zoom = if (fix != null) MapDefaults.BUILDING_ZOOM else MapDefaults.WORLD_ZOOM,
                footprints = true,
                onMapTap = { p -> onIntent(CaptureIntent.SelectBuilding(p.lat, p.lng)) },
                onFootprintTap = { p, id -> onIntent(CaptureIntent.SelectBuilding(p.lat, p.lng, id)) },
                modifier = Modifier.fillMaxWidth().height(420.dp),
            )
            // center "your location" marker
            Icon(BeaconIcons.Pin, contentDescription = null, tint = colors.primary, modifier = Modifier.align(Alignment.Center).size(34.dp))
            // GPS status chip — shows "Locating…" until a real fix arrives, then the live accuracy.
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.95f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (fix != null) colors.ok else colors.warn))
                Text(
                    when {
                        fix == null -> stringResource(Res.string.capture_locating_you)
                        draft.gpsAccuracyMeters > 0 -> "GPS ±${draft.gpsAccuracyMeters.toInt()} m"
                        else -> stringResource(Res.string.capture_location_set)
                    },
                    style = BeaconTheme.typography.caption, color = colors.ink2,
                )
            }
            // selected-building confirmation
            if (draft.buildingId != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape).background(colors.primary).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(BeaconIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
                    Text(stringResource(Res.string.capture_building_selected), style = BeaconTheme.typography.caption, color = colors.onPrimary)
                }
            }
        }
        // Anti-duplication warning when this building already has a recent report nearby.
        if (duplicateWarning != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.warnSoft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(BeaconIcons.Warning, contentDescription = null, tint = colors.warn, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.capture_dup_warning, duplicateWarning), style = BeaconTheme.typography.bodyS, color = colors.ink)
            }
        }
        // Plus Code (open, offline location code) + landmark fallback card
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(BeaconIcons.Pin, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Column {
                    Text(stringResource(Res.string.capture_w3w_label), style = BeaconTheme.typography.caption, color = colors.ink3)
                    Text(draft.plusCode.ifBlank { stringResource(Res.string.capture_locating) }, style = BeaconTheme.typography.mono, color = colors.primaryInk)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Spacer(Modifier.height(8.dp))
            LandmarkField(value = draft.landmark, onChange = { onIntent(CaptureIntent.SetLandmark(it)) })
        }
        // Guard helper: tell the user how to satisfy the location requirement when none is set yet.
        if (!hasLocation) {
            Text(
                stringResource(Res.string.capture_location_required),
                style = BeaconTheme.typography.caption,
                color = colors.warn,
            )
        }
    }
}

@Composable
private fun LandmarkField(value: String, onChange: (String) -> Unit) {
    val colors = BeaconTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(BeaconIcons.Edit, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(Res.string.capture_landmark_hint), style = BeaconTheme.typography.bodyS, color = colors.ink3)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = BeaconTheme.typography.bodyS.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Describe ───────────────────────────────────────────────────────────────────

@Composable
fun DescribeStep(
    draft: CaptureDraft,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    var text by remember { mutableStateOf(draft.description) }
    fun update(v: String) { text = v; onIntent(CaptureIntent.SetDescription(v)) }

    StepShell(
        current, total, onBack, onContinue, canContinue = true, continueLabel = stringResource(Res.string.step_continue),
        footer = {
            Text(
                stringResource(Res.string.capture_skip_desc),
                style = BeaconTheme.typography.label, color = colors.ink3,
                modifier = Modifier.clickable { onContinue() },
            )
        },
    ) {
        StepHeading(stringResource(Res.string.capture_describe_q), stringResource(Res.string.capture_describe_sub))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(14.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                if (text.isEmpty()) {
                    Text(stringResource(Res.string.capture_describe_hint), style = BeaconTheme.typography.bodyS, color = colors.ink3)
                }
                BasicTextField(
                    value = text, onValueChange = ::update,
                    textStyle = BeaconTheme.typography.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                Text("${text.length}/500", style = BeaconTheme.typography.caption, color = colors.ink3)
            }
        }
        // Write in any language — the server auto-translates it for analysts; the original is kept.
        Text(
            stringResource(Res.string.capture_describe_sub),
            style = BeaconTheme.typography.caption, color = colors.ink3,
        )
    }
}

// ── Review ───────────────────────────────────────────────────────────────────

@Composable
fun ReviewStep(
    draft: CaptureDraft,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    // Resolve localized labels in composable scope (joinToString lambda is not @Composable).
    val infraNames = mutableListOf<String>()
    for (t in draft.infra) infraNames.add(infraLabel(t))
    val crisisNames = mutableListOf<String>()
    for (n in draft.crisis) crisisNames.add(crisisLabel(n))
    val infraText = infraNames.joinToString().ifBlank { "—" }
    val crisisText = crisisNames.joinToString().ifBlank { "—" }
    StepShell(current, total, onBack, onContinue, canContinue = true, continueLabel = stringResource(Res.string.capture_submit), trailingIcon = null) {
        StepHeading(stringResource(Res.string.capture_review_q), stringResource(Res.string.capture_review_sub))
        // Photo preview — the real captured/picked photo.
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp))) {
            val photoPath = draft.photoPath
            if (photoPath != null) {
                CapturedImage(photoPath, Modifier.fillMaxWidth().height(150.dp))
            } else {
                com.stepanok.undp.designsystem.components.PhotoPlaceholder(modifier = Modifier.fillMaxWidth().height(150.dp), label = stringResource(Res.string.capture_photo_label))
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(BeaconIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(stringResource(Res.string.capture_anon_badge), style = BeaconTheme.typography.caption, color = Color.White)
            }
        }
        // Field rows
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(18.dp))) {
            ReviewRow(BeaconIcons.House, stringResource(Res.string.review_row_damage), draft.damageTier?.let { damageLabel(it) } ?: "—", valueColor = draft.damageTier?.let { colors.damageColor(it) })
            ReviewRow(BeaconIcons.Building, stringResource(Res.string.review_row_type), infraText)
            ReviewRow(BeaconIcons.Warning, stringResource(Res.string.review_row_crisis), crisisText)
            ReviewRow(BeaconIcons.Truck, stringResource(Res.string.review_row_debris), draft.debris?.let { debrisLabel(it) } ?: "—")
            ReviewRow(BeaconIcons.Pin, stringResource(Res.string.review_row_location), draft.plusCode.ifBlank { "—" } + if (draft.gpsAccuracyMeters > 0) " · ±${draft.gpsAccuracyMeters.toInt()} m" else "")
            ReviewRow(BeaconIcons.Edit, stringResource(Res.string.review_row_description), draft.description.ifBlank { "—" }, last = true)
        }
        // Privacy reassurance
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface2).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(BeaconIcons.Shield, contentDescription = null, tint = colors.ok, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.capture_privacy), style = BeaconTheme.typography.caption, color = colors.ink2)
        }
    }
}

@Composable
private fun ReviewRow(icon: ImageVector, label: String, value: String, valueColor: Color? = null, last: Boolean = false) {
    val colors = BeaconTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface2), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = valueColor ?: colors.ink2, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = BeaconTheme.typography.caption, color = colors.ink3)
                Text(value, style = BeaconTheme.typography.bodyS, color = valueColor ?: colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(BeaconIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}
