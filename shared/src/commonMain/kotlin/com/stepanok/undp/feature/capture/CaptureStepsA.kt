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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.components.SelectableOptionRow
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.InfraType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.capture_crisis_q
import undp.shared.generated.resources.capture_crisis_sub
import undp.shared.generated.resources.capture_damage_q
import undp.shared.generated.resources.capture_damage_sub
import undp.shared.generated.resources.capture_debris_q
import undp.shared.generated.resources.capture_debris_sub
import undp.shared.generated.resources.capture_infra_q
import undp.shared.generated.resources.capture_infra_sub
import undp.shared.generated.resources.crisis_chemical
import undp.shared.generated.resources.crisis_conflict
import undp.shared.generated.resources.crisis_earthquake
import undp.shared.generated.resources.crisis_explosion
import undp.shared.generated.resources.crisis_flood
import undp.shared.generated.resources.crisis_group_humanmade
import undp.shared.generated.resources.crisis_group_natural
import undp.shared.generated.resources.crisis_group_technological
import undp.shared.generated.resources.crisis_hurricane
import undp.shared.generated.resources.crisis_tsunami
import undp.shared.generated.resources.crisis_unrest
import undp.shared.generated.resources.crisis_wildfire
import undp.shared.generated.resources.damage_tier_minimal
import undp.shared.generated.resources.damage_tier_minimal_desc
import undp.shared.generated.resources.damage_tier_partial
import undp.shared.generated.resources.damage_tier_partial_desc
import undp.shared.generated.resources.damage_tier_complete
import undp.shared.generated.resources.damage_tier_complete_desc
import undp.shared.generated.resources.capture_possibly_damaged
import undp.shared.generated.resources.capture_possibly_damaged_desc
import undp.shared.generated.resources.debris_no
import undp.shared.generated.resources.debris_no_desc
import undp.shared.generated.resources.debris_unsure
import undp.shared.generated.resources.debris_unsure_desc
import undp.shared.generated.resources.debris_yes
import undp.shared.generated.resources.debris_yes_desc
import undp.shared.generated.resources.infra_commercial
import undp.shared.generated.resources.infra_commercial_desc
import undp.shared.generated.resources.infra_community
import undp.shared.generated.resources.infra_community_desc
import undp.shared.generated.resources.infra_government
import undp.shared.generated.resources.infra_government_desc
import undp.shared.generated.resources.infra_name_hint
import undp.shared.generated.resources.infra_other
import undp.shared.generated.resources.infra_other_desc
import undp.shared.generated.resources.infra_other_hint
import undp.shared.generated.resources.infra_public
import undp.shared.generated.resources.infra_public_desc
import undp.shared.generated.resources.infra_residential
import undp.shared.generated.resources.infra_residential_desc
import undp.shared.generated.resources.infra_transport
import undp.shared.generated.resources.infra_transport_desc
import undp.shared.generated.resources.infra_utility
import undp.shared.generated.resources.infra_utility_desc
import undp.shared.generated.resources.step_change_later

// ── Damage ──────────────────────────────────────────────────────────────────

@Composable
fun DamageStep(
    draft: CaptureDraft,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    StepShell(
        current, total, onBack, onContinue,
        canContinue = draft.damageTier != null,
        footer = { Text(stringResource(Res.string.step_change_later), style = BeaconTheme.typography.caption, color = colors.ink3) },
    ) {
        StepHeading(stringResource(Res.string.capture_damage_q), stringResource(Res.string.capture_damage_sub))
        // The mandated 3-tier classification (minimal / partial / complete).
        val tiers = listOf(
            DamageTier.MINIMAL to (Res.string.damage_tier_minimal to Res.string.damage_tier_minimal_desc),
            DamageTier.PARTIAL to (Res.string.damage_tier_partial to Res.string.damage_tier_partial_desc),
            DamageTier.COMPLETE to (Res.string.damage_tier_complete to Res.string.damage_tier_complete_desc),
        )
        tiers.forEach { (tier, labels) ->
            SelectableOptionRow(
                title = stringResource(labels.first),
                description = stringResource(labels.second),
                icon = BeaconIcons.House,
                selected = draft.damageTier == tier,
                accent = colors.damageColor(tier),
                softAccent = colors.damageSoft(tier),
                onClick = { onIntent(CaptureIntent.SetDamageTier(tier)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        // Confidence: reporter unsure of the exact grade.
        SelectableOptionRow(
            title = stringResource(Res.string.capture_possibly_damaged),
            description = stringResource(Res.string.capture_possibly_damaged_desc),
            icon = BeaconIcons.House,
            selected = draft.possiblyDamaged,
            accent = colors.warn,
            softAccent = colors.warnSoft,
            onClick = { onIntent(CaptureIntent.SetPossiblyDamaged(!draft.possiblyDamaged)) },
        )
    }
}

// ── Infrastructure (multi-select grid) ───────────────────────────────────────

private data class InfraOption(val type: InfraType, val label: StringResource, val desc: StringResource, val icon: ImageVector)

@Composable
fun InfraStep(
    draft: CaptureDraft,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val options = listOf(
        InfraOption(InfraType.RESIDENTIAL, Res.string.infra_residential, Res.string.infra_residential_desc, BeaconIcons.House),
        InfraOption(InfraType.COMMERCIAL, Res.string.infra_commercial, Res.string.infra_commercial_desc, BeaconIcons.Shop),
        InfraOption(InfraType.GOVERNMENT, Res.string.infra_government, Res.string.infra_government_desc, BeaconIcons.Building),
        InfraOption(InfraType.UTILITY, Res.string.infra_utility, Res.string.infra_utility_desc, BeaconIcons.Bolt),
        InfraOption(InfraType.TRANSPORT, Res.string.infra_transport, Res.string.infra_transport_desc, BeaconIcons.Road),
        InfraOption(InfraType.COMMUNITY, Res.string.infra_community, Res.string.infra_community_desc, BeaconIcons.Hospital),
        InfraOption(InfraType.PUBLIC, Res.string.infra_public, Res.string.infra_public_desc, BeaconIcons.Park),
        InfraOption(InfraType.OTHER, Res.string.infra_other, Res.string.infra_other_desc, BeaconIcons.More),
    )
    StepShell(current, total, onBack, onContinue, canContinue = draft.infra.isNotEmpty()) {
        StepHeading(stringResource(Res.string.capture_infra_q), stringResource(Res.string.capture_infra_sub))
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { opt ->
                    InfraCell(
                        label = stringResource(opt.label),
                        desc = stringResource(opt.desc),
                        icon = opt.icon,
                        selected = opt.type in draft.infra,
                        onClick = { onIntent(CaptureIntent.ToggleInfra(opt.type)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // One optional name/details field for ANY selected type; the hint adapts when OTHER is
        // picked (the same text then also feeds the legacy "specify Other" wire field).
        if (draft.infra.isNotEmpty()) {
            val colors = BeaconTheme.colors
            val hint = stringResource(
                if (InfraType.OTHER in draft.infra) Res.string.infra_other_hint else Res.string.infra_name_hint,
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(BeaconIcons.Edit, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f)) {
                    if (draft.infraName.isEmpty()) {
                        Text(hint, style = BeaconTheme.typography.bodyS, color = colors.ink3)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = draft.infraName,
                        onValueChange = { onIntent(CaptureIntent.SetInfraName(it)) },
                        textStyle = BeaconTheme.typography.bodyS.copy(color = colors.ink),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfraCell(label: String, desc: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BeaconTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primarySoft else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.7f) else colors.surface2),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(label, style = BeaconTheme.typography.titleS, color = colors.ink)
            Text(desc, style = BeaconTheme.typography.caption, color = colors.ink3)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(BeaconIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        }
    }
}

// ── Crisis nature (grouped multi-select) ──────────────────────────────────────

private data class CrisisOption(val nature: CrisisNature, val label: StringResource, val icon: ImageVector)

@Composable
fun CrisisStep(
    draft: CaptureDraft,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val groups = listOf(
        Res.string.crisis_group_natural to listOf(
            CrisisOption(CrisisNature.EARTHQUAKE, Res.string.crisis_earthquake, BeaconIcons.Earthquake),
            CrisisOption(CrisisNature.FLOOD, Res.string.crisis_flood, BeaconIcons.Flood),
            CrisisOption(CrisisNature.TSUNAMI, Res.string.crisis_tsunami, BeaconIcons.Flood),
            CrisisOption(CrisisNature.HURRICANE, Res.string.crisis_hurricane, BeaconIcons.Wind),
            CrisisOption(CrisisNature.WILDFIRE, Res.string.crisis_wildfire, BeaconIcons.Flame),
        ),
        Res.string.crisis_group_technological to listOf(
            CrisisOption(CrisisNature.EXPLOSION, Res.string.crisis_explosion, BeaconIcons.Explosion),
            CrisisOption(CrisisNature.CHEMICAL, Res.string.crisis_chemical, BeaconIcons.Chemical),
        ),
        Res.string.crisis_group_humanmade to listOf(
            CrisisOption(CrisisNature.CONFLICT, Res.string.crisis_conflict, BeaconIcons.Conflict),
            CrisisOption(CrisisNature.CIVIL_UNREST, Res.string.crisis_unrest, BeaconIcons.Flag),
        ),
    )
    StepShell(current, total, onBack, onContinue, canContinue = draft.crisis.isNotEmpty()) {
        StepHeading(stringResource(Res.string.capture_crisis_q), stringResource(Res.string.capture_crisis_sub))
        groups.forEach { (groupLabel, items) ->
            Text(
                stringResource(groupLabel).uppercase(),
                style = BeaconTheme.typography.micro,
                color = BeaconTheme.colors.ink3,
            )
            items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { opt ->
                        CrisisCell(
                            label = stringResource(opt.label),
                            icon = opt.icon,
                            selected = opt.nature in draft.crisis,
                            onClick = { onIntent(CaptureIntent.ToggleCrisis(opt.nature)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CrisisCell(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BeaconTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primarySoft else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.ink2, modifier = Modifier.size(18.dp))
        Text(label, style = BeaconTheme.typography.label, color = colors.ink)
    }
}

// ── Debris ───────────────────────────────────────────────────────────────────

@Composable
fun DebrisStep(
    draft: CaptureDraft,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    StepShell(current, total, onBack, onContinue, canContinue = draft.debris != null) {
        StepHeading(stringResource(Res.string.capture_debris_q), stringResource(Res.string.capture_debris_sub))
        SelectableOptionRow(
            title = stringResource(Res.string.debris_yes), description = stringResource(Res.string.debris_yes_desc),
            icon = BeaconIcons.Truck, selected = draft.debris == DebrisState.YES,
            accent = colors.complete, softAccent = colors.completeSoft,
            onClick = { onIntent(CaptureIntent.SetDebris(DebrisState.YES)) },
        )
        SelectableOptionRow(
            title = stringResource(Res.string.debris_no), description = stringResource(Res.string.debris_no_desc),
            // Leaf, not a checkmark: a green Check leading-icon read as a "selected" tick, making
            // "No" look pre-committed while the trailing radio (the real indicator) was still empty.
            icon = BeaconIcons.Leaf, selected = draft.debris == DebrisState.NO,
            accent = colors.ok, softAccent = colors.okSoft,
            onClick = { onIntent(CaptureIntent.SetDebris(DebrisState.NO)) },
        )
        SelectableOptionRow(
            title = stringResource(Res.string.debris_unsure), description = stringResource(Res.string.debris_unsure_desc),
            icon = BeaconIcons.Info, selected = draft.debris == DebrisState.UNSURE,
            accent = colors.primary, softAccent = colors.primarySoft,
            onClick = { onIntent(CaptureIntent.SetDebris(DebrisState.UNSURE)) },
        )
    }
}
