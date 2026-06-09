package com.stepanok.undp.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.core.media.BeaconBackHandler
import com.stepanok.undp.core.media.CameraPreview
import com.stepanok.undp.core.media.RequestCameraPermission
import com.stepanok.undp.core.media.rememberCameraHandle
import com.stepanok.undp.core.media.rememberPhotoCapture
import com.stepanok.undp.core.mvi.collectAsEffect
import com.stepanok.undp.designsystem.components.BeaconButton
import com.stepanok.undp.designsystem.components.BeaconButtonSize
import com.stepanok.undp.designsystem.safeBottomPadding
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.components.BeaconButtonVariant
import com.stepanok.undp.designsystem.components.BeaconProgressBar
import com.stepanok.undp.designsystem.components.BeaconStepper
import com.stepanok.undp.designsystem.components.PhotoPlaceholder
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.designsystem.theme.beaconPopShadow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.capture_badge_progress
import undp.shared.generated.resources.capture_camera_hint
import undp.shared.generated.resources.capture_capture_btn
import undp.shared.generated.resources.capture_guide_closeup
import undp.shared.generated.resources.capture_guide_no_people
import undp.shared.generated.resources.capture_guide_whole
import undp.shared.generated.resources.capture_new_report
import undp.shared.generated.resources.capture_phase_damage
import undp.shared.generated.resources.capture_phase_details
import undp.shared.generated.resources.capture_phase_location
import undp.shared.generated.resources.capture_points
import undp.shared.generated.resources.capture_submitted_body
import undp.shared.generated.resources.capture_submitted_offline_body
import undp.shared.generated.resources.capture_submitted_offline_title
import undp.shared.generated.resources.capture_submitted_title
import undp.shared.generated.resources.capture_submitting
import undp.shared.generated.resources.capture_view_map
import undp.shared.generated.resources.step_continue

private val CAPTURE_ORDER = listOf("damage", "infra", "crisis", "debris", "location", "describe", "modular", "review")

/**
 * The 8 sub-screens grouped into the 3 phases onboarding promises ("Three steps. No fuss."):
 * Damage (damage/infra/crisis/debris) → Location (location) → Details (describe/modular/review).
 * The header renders progress per-PHASE (3 stepper segments + "<Phase> · i/j"), so the visible
 * counter never contradicts the onboarding copy by reading "x/8".
 */
private data class CapturePhase(val label: StringResource, val firstIndex: Int, val stepCount: Int)

private val CAPTURE_PHASES = listOf(
    CapturePhase(Res.string.capture_phase_damage, firstIndex = 0, stepCount = 4),
    CapturePhase(Res.string.capture_phase_location, firstIndex = 4, stepCount = 1),
    CapturePhase(Res.string.capture_phase_details, firstIndex = 5, stepCount = 3),
)

object CaptureFlowScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<CaptureFlowScreenModel>()
        val state by model.state.collectAsState()
        val nav = LocalNavigator.currentOrThrow
        var step by remember { mutableStateOf("camera") }

        model.effects.collectAsEffect { effect ->
            when (effect) {
                CaptureEffect.Submitted -> step = "submitted"
                is CaptureEffect.Error -> {}
            }
        }

        // Intercept system/gesture back so it steps BACK one wizard step instead of letting
        // Voyager pop the whole CaptureFlowScreen (which would silently discard the draft).
        // Mirrors the on-screen back arrow; only the entry/submitted steps exit via pop.
        // Disabled while submitting so back can't fire mid-upload.
        BeaconBackHandler(enabled = !state.submitting) {
            when (step) {
                "camera", "submitted" -> nav.pop()
                else -> {
                    val idx = CAPTURE_ORDER.indexOf(step)
                    step = if (idx <= 0) "camera" else CAPTURE_ORDER[idx - 1]
                }
            }
        }

        when (step) {
            "camera" -> CameraStep(
                onCaptured = { path, size ->
                    model.onIntent(CaptureIntent.PhotoCaptured(path, size)); step = "damage"
                },
                onClose = { nav.pop() },
            )
            "submitted" -> SubmittedStep(offline = state.offline, onDone = { nav.pop() })
            else -> {
                val idx = CAPTURE_ORDER.indexOf(step)
                val onBack: () -> Unit = { step = if (idx == 0) "camera" else CAPTURE_ORDER[idx - 1] }
                val onContinue: () -> Unit = {
                    if (step == "review") model.onIntent(CaptureIntent.Submit) else step = CAPTURE_ORDER[idx + 1]
                }
                Box(Modifier.fillMaxSize()) {
                    CaptureStep(
                        step = step,
                        current = idx,
                        total = CAPTURE_ORDER.size,
                        draft = state.draft,
                        damageScale = state.damageScale,
                        duplicateWarning = state.duplicateWarning,
                        onIntent = model::onIntent,
                        onBack = onBack,
                        onContinue = onContinue,
                    )
                    if (state.submitting) {
                        // Block interaction + show progress while the report uploads.
                        Box(
                            Modifier.fillMaxSize().background(Color(0xCC1A1430)).clickable(enabled = false) {},
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                                Text(stringResource(Res.string.capture_submitting), style = BeaconTheme.typography.label, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureStep(
    step: String,
    current: Int,
    total: Int,
    draft: CaptureDraft,
    damageScale: String,
    duplicateWarning: String?,
    onIntent: (CaptureIntent) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    when (step) {
        "damage" -> DamageStep(draft, damageScale, onIntent, current, total, onBack, onContinue)
        "infra" -> InfraStep(draft, onIntent, current, total, onBack, onContinue)
        "crisis" -> CrisisStep(draft, onIntent, current, total, onBack, onContinue)
        "debris" -> DebrisStep(draft, onIntent, current, total, onBack, onContinue)
        "location" -> LocationStep(draft, duplicateWarning, onIntent, current, total, onBack, onContinue)
        "describe" -> DescribeStep(draft, onIntent, current, total, onBack, onContinue)
        "modular" -> ModularStep(draft, onIntent, current, total, onBack, onContinue)
        "review" -> ReviewStep(draft, current, total, onBack, onContinue)
    }
}

/** Shared shell for the multi-select steps: back + progress + scrollable content + Continue. */
@Composable
fun StepShell(
    current: Int,
    total: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    canContinue: Boolean,
    continueLabel: String = stringResource(Res.string.step_continue),
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = BeaconIcons.ArrowRight,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BeaconTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier.fillMaxWidth().safeTopPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(colors.surface2).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BeaconIcons.ArrowLeft, contentDescription = "Back", tint = colors.ink, modifier = Modifier.size(18.dp))
            }
            // Phase-grouped progress: 3 segments (one per phase) + a per-phase counter.
            // `total` (the raw sub-screen count) is intentionally NOT shown to the user.
            val phaseIdx = CAPTURE_PHASES.indexOfLast { current >= it.firstIndex }.coerceAtLeast(0)
            val phase = CAPTURE_PHASES[phaseIdx]
            BeaconStepper(current = phaseIdx, total = CAPTURE_PHASES.size, modifier = Modifier.weight(1f))
            val phaseLabel = stringResource(phase.label)
            Text(
                if (phase.stepCount > 1) "$phaseLabel · ${current - phase.firstIndex + 1}/${phase.stepCount}" else phaseLabel,
                style = BeaconTheme.typography.caption,
                color = colors.ink3,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        Column(Modifier.fillMaxWidth().background(colors.bg).safeBottomPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            BeaconButton(
                text = continueLabel,
                onClick = onContinue,
                enabled = canContinue,
                fullWidth = true,
                size = BeaconButtonSize.Lg,
                trailingIcon = trailingIcon,
            )
            if (footer != null) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { footer() }
            }
        }
    }
}

@Composable
fun StepHeading(title: String, subtitle: String) {
    val colors = BeaconTheme.colors
    Text(title, style = BeaconTheme.typography.titleL, color = colors.ink)
    Text(subtitle, style = BeaconTheme.typography.bodyS, color = colors.ink2)
    Spacer(Modifier.height(6.dp))
}

// ── Camera ──────────────────────────────────────────────────────────────────

@Composable
private fun CameraStep(onCaptured: (String, Long) -> Unit, onClose: () -> Unit) {
    // Live in-app camera (CameraX / AVFoundation). The gallery icon still falls back to the OS picker.
    val photoCapture = rememberPhotoCapture { photo ->
        if (photo != null) onCaptured(photo.path, photo.sizeBytes)
    }
    var granted by remember { mutableStateOf(false) }
    RequestCameraPermission { granted = it }

    val camera = rememberCameraHandle()
    val available by camera.available.collectAsState()
    val torchOn by camera.torchOn.collectAsState()
    val hasTorch by camera.hasTorch.collectAsState()
    val live = granted && available

    Box(Modifier.fillMaxSize().background(Color(0xFF0C0C10))) {
        // Compose the preview as soon as permission is granted — binding the camera is what
        // flips `available`, so it must run regardless of the (initially false) available state.
        if (granted) {
            CameraPreview(camera, Modifier.fillMaxSize())
        }
        if (!granted) {
            PhotoPlaceholder(modifier = Modifier.fillMaxSize(), dark = true, label = "allow camera access to take a photo")
        } else if (!available) {
            PhotoPlaceholder(modifier = Modifier.fillMaxSize(), dark = true, label = "no camera available — use the gallery")
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).safeTopPadding().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live && hasTorch) {
                CircleControl(
                    BeaconIcons.Flash,
                    onClick = { camera.toggleTorch() },
                    tint = if (torchOn) BeaconTheme.colors.warn else Color.White,
                )
            } else {
                Spacer(Modifier.size(44.dp))
            }
            CircleControl(BeaconIcons.Close, onClick = onClose)
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.clip(CircleShape).background(Color(0x8C000000)).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(BeaconIcons.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(stringResource(Res.string.capture_camera_hint), style = BeaconTheme.typography.caption, color = Color.White)
                }
            }
            // Capture guidance — whole building first, then a close-up; never people (privacy).
            CameraGuideChip(stringResource(Res.string.capture_guide_whole))
            CameraGuideChip(stringResource(Res.string.capture_guide_closeup))
            CameraGuideChip(stringResource(Res.string.capture_guide_no_people))
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeBottomPadding().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleControl(BeaconIcons.Image, onClick = { photoCapture.pickFromLibrary() }, bg = Color(0x1FFFFFFF))
            Box(
                Modifier.size(78.dp).clip(CircleShape)
                    .background(if (live) BeaconTheme.colors.primary else BeaconTheme.colors.primary.copy(alpha = 0.4f))
                    .clickable(enabled = live) {
                        camera.capture { photo -> if (photo != null) onCaptured(photo.path, photo.sizeBytes) }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(BeaconIcons.Camera, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(30.dp)) }
            CircleControl(
                BeaconIcons.FlipCamera,
                onClick = { if (live) camera.switchLens() },
                bg = Color(0x1FFFFFFF),
            )
        }
    }
}

/** Subtle translucent caption chip for the camera-framing guidance. */
@Composable
private fun CameraGuideChip(text: String) {
    Box(
        Modifier.clip(CircleShape).background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = BeaconTheme.typography.caption, color = Color(0xE6FFFFFF))
    }
}

@Composable
private fun CircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    bg: Color = Color(0x1AFFFFFF),
    tint: Color = Color.White,
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

// ── Submitted ───────────────────────────────────────────────────────────────

@Composable
private fun SubmittedStep(offline: Boolean, onDone: () -> Unit) {
    val colors = BeaconTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.primarySoft).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).beaconPopShadow(CircleShape).clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (offline) BeaconIcons.CloudUp else BeaconIcons.Check,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (offline) Res.string.capture_submitted_offline_title else Res.string.capture_submitted_title),
            style = BeaconTheme.typography.titleL, color = colors.ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (offline) Res.string.capture_submitted_offline_body else Res.string.capture_submitted_body),
            style = BeaconTheme.typography.body, color = colors.ink2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        // Reward card
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(colors.warn),
                contentAlignment = Alignment.Center,
            ) { Icon(BeaconIcons.Medal, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.capture_points), style = BeaconTheme.typography.titleS, color = colors.ink)
                Text("${stringResource(Res.string.capture_badge_progress)} · 60%", style = BeaconTheme.typography.caption, color = colors.ink3)
                Spacer(Modifier.height(6.dp))
                BeaconProgressBar(progress = 0.6f, color = colors.warn)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BeaconButton(
                text = stringResource(Res.string.capture_view_map), onClick = onDone,
                variant = BeaconButtonVariant.Secondary, fullWidth = true, trailingIcon = null,
                modifier = Modifier.weight(1f),
            )
            BeaconButton(
                text = stringResource(Res.string.capture_new_report), onClick = onDone,
                leadingIcon = BeaconIcons.Plus, fullWidth = true, trailingIcon = null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
