package com.stepanok.undp.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.ElectricityCondition
import com.stepanok.undp.domain.model.HealthServices
import com.stepanok.undp.domain.model.PressingNeed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.capture_modular_q
import undp.shared.generated.resources.capture_modular_sub
import undp.shared.generated.resources.capture_skip_modular
import undp.shared.generated.resources.elec_destroyed
import undp.shared.generated.resources.elec_minor
import undp.shared.generated.resources.elec_moderate
import undp.shared.generated.resources.elec_none
import undp.shared.generated.resources.elec_severe
import undp.shared.generated.resources.elec_unknown
import undp.shared.generated.resources.health_disrupted
import undp.shared.generated.resources.health_full
import undp.shared.generated.resources.health_none
import undp.shared.generated.resources.health_partial
import undp.shared.generated.resources.health_unknown
import undp.shared.generated.resources.modular_electricity
import undp.shared.generated.resources.modular_health
import undp.shared.generated.resources.modular_needs
import undp.shared.generated.resources.need_cash
import undp.shared.generated.resources.need_food_water
import undp.shared.generated.resources.need_healthcare
import undp.shared.generated.resources.need_livelihoods
import undp.shared.generated.resources.need_local
import undp.shared.generated.resources.need_other
import undp.shared.generated.resources.need_protection
import undp.shared.generated.resources.need_shelter
import undp.shared.generated.resources.need_wash
import undp.shared.generated.resources.step_continue

private val ELEC = listOf(
    ElectricityCondition.NONE_OBSERVED to Res.string.elec_none,
    ElectricityCondition.MINOR to Res.string.elec_minor,
    ElectricityCondition.MODERATE to Res.string.elec_moderate,
    ElectricityCondition.SEVERE to Res.string.elec_severe,
    ElectricityCondition.DESTROYED to Res.string.elec_destroyed,
    ElectricityCondition.UNKNOWN to Res.string.elec_unknown,
)
private val HEALTH = listOf(
    HealthServices.FULLY_FUNCTIONAL to Res.string.health_full,
    HealthServices.PARTIALLY_FUNCTIONAL to Res.string.health_partial,
    HealthServices.LARGELY_DISRUPTED to Res.string.health_disrupted,
    HealthServices.NOT_FUNCTIONING to Res.string.health_none,
    HealthServices.UNKNOWN to Res.string.health_unknown,
)
private val NEEDS = listOf(
    PressingNeed.FOOD_WATER to Res.string.need_food_water,
    PressingNeed.CASH to Res.string.need_cash,
    PressingNeed.HEALTHCARE to Res.string.need_healthcare,
    PressingNeed.SHELTER to Res.string.need_shelter,
    PressingNeed.LIVELIHOODS to Res.string.need_livelihoods,
    PressingNeed.WASH to Res.string.need_wash,
    PressingNeed.PROTECTION to Res.string.need_protection,
    PressingNeed.LOCAL_SUPPORT to Res.string.need_local,
    PressingNeed.OTHER to Res.string.need_other,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModularStep(
    draft: CaptureDraft,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    StepShell(
        current, total, onBack, onContinue, canContinue = true, continueLabel = stringResource(Res.string.step_continue),
        footer = {
            Text(
                stringResource(Res.string.capture_skip_modular),
                style = BeaconTheme.typography.label, color = colors.ink3,
                modifier = Modifier.clickable { onContinue() },
            )
        },
    ) {
        StepHeading(stringResource(Res.string.capture_modular_q), stringResource(Res.string.capture_modular_sub))

        Section(stringResource(Res.string.modular_electricity)) {
            ELEC.forEach { (value, label) ->
                ChoiceChip(stringResource(label), selected = draft.electricity == value) { onIntent(CaptureIntent.SetElectricity(value)) }
            }
        }
        Section(stringResource(Res.string.modular_health)) {
            HEALTH.forEach { (value, label) ->
                ChoiceChip(stringResource(label), selected = draft.health == value) { onIntent(CaptureIntent.SetHealth(value)) }
            }
        }
        Section(stringResource(Res.string.modular_needs)) {
            NEEDS.forEach { (value, label) ->
                ChoiceChip(stringResource(label), selected = value in draft.needs) { onIntent(CaptureIntent.ToggleNeed(value)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable FlowRowScope.() -> Unit) {
    val colors = BeaconTheme.colors
    Text(title.uppercase(), style = BeaconTheme.typography.micro, color = colors.ink3)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BeaconTheme.colors
    Text(
        label,
        style = BeaconTheme.typography.label,
        color = if (selected) colors.onPrimary else colors.ink2,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surface)
            .border(1.dp, if (selected) colors.primary else colors.line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
