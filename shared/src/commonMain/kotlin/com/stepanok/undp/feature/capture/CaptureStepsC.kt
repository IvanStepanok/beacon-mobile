package com.stepanok.undp.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.FORM_OTHER_VALUE
import com.stepanok.undp.domain.model.FormOption
import com.stepanok.undp.domain.model.FormSection
import com.stepanok.undp.domain.model.FormSectionType
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
import undp.shared.generated.resources.modular_required_hint
import undp.shared.generated.resources.need_basic_services
import undp.shared.generated.resources.need_cash
import undp.shared.generated.resources.need_food_water
import undp.shared.generated.resources.need_healthcare
import undp.shared.generated.resources.need_livelihoods
import undp.shared.generated.resources.need_local
import undp.shared.generated.resources.need_other
import undp.shared.generated.resources.need_other_hint
import undp.shared.generated.resources.need_protection
import undp.shared.generated.resources.need_shelter
import undp.shared.generated.resources.need_wash
import undp.shared.generated.resources.step_continue

// Localized labels for the BUILT-IN sections/options, so the three Appendix-1 questions look
// exactly as before in all six languages. The schema's (English) labels are the fallback for
// anything UNDP adds server-side that this build doesn't know yet.
private val SECTION_TITLES: Map<String, StringResource> = mapOf(
    "electricity" to Res.string.modular_electricity,
    "healthServices" to Res.string.modular_health,
    "pressingNeeds" to Res.string.modular_needs,
)
private val OPTION_LABELS: Map<String, Map<String, StringResource>> = mapOf(
    "electricity" to mapOf(
        "none_observed" to Res.string.elec_none,
        "minor" to Res.string.elec_minor,
        "moderate" to Res.string.elec_moderate,
        "severe" to Res.string.elec_severe,
        "destroyed" to Res.string.elec_destroyed,
        "unknown" to Res.string.elec_unknown,
    ),
    "healthServices" to mapOf(
        "fully_functional" to Res.string.health_full,
        "partially_functional" to Res.string.health_partial,
        "largely_disrupted" to Res.string.health_disrupted,
        "not_functioning" to Res.string.health_none,
        "unknown" to Res.string.health_unknown,
    ),
    "pressingNeeds" to mapOf(
        "food_water" to Res.string.need_food_water,
        "cash" to Res.string.need_cash,
        "healthcare" to Res.string.need_healthcare,
        "shelter" to Res.string.need_shelter,
        "livelihoods" to Res.string.need_livelihoods,
        "wash" to Res.string.need_wash,
        "protection" to Res.string.need_protection,
        "local_support" to Res.string.need_local,
        "basic_services" to Res.string.need_basic_services,
        "other" to Res.string.need_other,
    ),
)

@Composable
private fun sectionTitle(section: FormSection): String =
    SECTION_TITLES[section.key]?.let { stringResource(it) } ?: section.title

@Composable
private fun optionLabel(sectionKey: String, option: FormOption): String =
    OPTION_LABELS[sectionKey]?.get(option.value)?.let { stringResource(it) } ?: option.label

/** A section counts as ANSWERED for required-gating only if a real option is selected: a bare
 *  "other" chip with empty free text says nothing, so it must not satisfy a required section.
 *  (Optional sections still submit whatever was picked — this gates Continue only.) */
private fun FormSection.isAnswered(draft: CaptureDraft): Boolean {
    val selected = draft.modularAnswers[key].orEmpty()
    if (selected.isEmpty()) return false
    val onlyOther = allowOtherText && selected.all { it == FORM_OTHER_VALUE }
    return !onlyOther || draft.modularOther[key].orEmpty().isNotBlank()
}

/** Renders [sections] straight from the server's form schema: single-choice chips for
 *  type=single, toggle chips for multi, a free-text field while an allowOtherText section's
 *  "other" chip is selected. Unknown (server-added) sections render generically and their
 *  answers flow into the modular blob under the section key. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModularStep(
    draft: CaptureDraft,
    sections: List<FormSection>,
    onIntent: (CaptureIntent) -> Unit,
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = BeaconTheme.colors
    // Required sections (a per-crisis override) gate Continue, and the whole-step "Skip these"
    // shortcut disappears as soon as any section is required (skipping would bypass the gate).
    val anyRequired = sections.any { it.required }
    val canContinue = sections.none { it.required && !it.isAnswered(draft) }
    StepShell(
        current, total, onBack, onContinue, canContinue = canContinue, continueLabel = stringResource(Res.string.step_continue),
        footer = if (anyRequired) {
            null
        } else {
            {
                Text(
                    stringResource(Res.string.capture_skip_modular),
                    style = BeaconTheme.typography.label, color = colors.ink3,
                    modifier = Modifier.clickable { onContinue() },
                )
            }
        },
    ) {
        // The "Optional — skip if unsure" subtitle would contradict a required section.
        StepHeading(
            stringResource(Res.string.capture_modular_q),
            if (anyRequired) "" else stringResource(Res.string.capture_modular_sub),
        )

        sections.forEach { section ->
            val selected = draft.modularAnswers[section.key].orEmpty()
            // Required sections are marked with an asterisk (and gate Continue above).
            Section(sectionTitle(section) + if (section.required) " *" else "") {
                section.options.forEach { option ->
                    ChoiceChip(optionLabel(section.key, option), selected = option.value in selected) {
                        onIntent(
                            if (section.type == FormSectionType.SINGLE) {
                                CaptureIntent.SetModularChoice(section.key, option.value)
                            } else {
                                CaptureIntent.ToggleModularOption(section.key, option.value)
                            },
                        )
                    }
                }
            }
            // "Other → specify" free text, shown only while the section's Other chip is selected.
            if (section.allowOtherText && FORM_OTHER_VALUE in selected) {
                OtherTextField(
                    value = draft.modularOther[section.key].orEmpty(),
                    onChange = { onIntent(CaptureIntent.SetModularOther(section.key, it)) },
                )
            }
            // A bare asterisk doesn't explain WHY Continue is disabled — say it under the section.
            if (section.required && !section.isAnswered(draft)) {
                RequiredHint()
            }
        }
    }
}

/** Inline nudge under an unanswered REQUIRED section, in the warn color (not an error shout). */
@Composable
private fun RequiredHint() {
    val colors = BeaconTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(BeaconIcons.Info, contentDescription = null, tint = colors.warn, modifier = Modifier.size(12.dp))
        Text(stringResource(Res.string.modular_required_hint), style = BeaconTheme.typography.caption, color = colors.warn)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun OtherTextField(value: String, onChange: (String) -> Unit) {
    val colors = BeaconTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(BeaconIcons.Edit, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(Res.string.need_other_hint), style = BeaconTheme.typography.bodyS, color = colors.ink3)
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
