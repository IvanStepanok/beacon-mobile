package com.stepanok.undp.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.designsystem.components.BeaconButton
import com.stepanok.undp.designsystem.components.BeaconButtonSize
import com.stepanok.undp.designsystem.components.BeaconButtonVariant
import com.stepanok.undp.core.storage.PrefKeys
import com.stepanok.undp.core.storage.Prefs
import com.stepanok.undp.designsystem.safeBottomPadding
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.designsystem.theme.beaconPopShadow
import com.stepanok.undp.feature.main.MainShellScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.common_get_started
import undp.shared.generated.resources.common_next
import undp.shared.generated.resources.common_skip
import undp.shared.generated.resources.ob_s1_body
import undp.shared.generated.resources.ob_s1_eyebrow
import undp.shared.generated.resources.ob_s1_title
import undp.shared.generated.resources.ob_s2_body
import undp.shared.generated.resources.ob_s2_eyebrow
import undp.shared.generated.resources.ob_s2_title
import undp.shared.generated.resources.ob_s3_body
import undp.shared.generated.resources.ob_s3_eyebrow
import undp.shared.generated.resources.ob_s3_title
import undp.shared.generated.resources.ob_s4_body
import undp.shared.generated.resources.ob_s4_eyebrow
import undp.shared.generated.resources.ob_s4_title
import undp.shared.generated.resources.onboarding_1
import undp.shared.generated.resources.onboarding_2
import undp.shared.generated.resources.onboarding_3
import undp.shared.generated.resources.onboarding_4

private data class OnboardingSlide(
    val image: DrawableResource,
    val icon: ImageVector,
    val eyebrow: String,
    val title: String,
    val body: String,
)

object OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val colors = BeaconTheme.colors
        val scope = rememberCoroutineScope()

        val slides = listOf(
            OnboardingSlide(Res.drawable.onboarding_1, BeaconIcons.Leaf, stringResource(Res.string.ob_s1_eyebrow), stringResource(Res.string.ob_s1_title), stringResource(Res.string.ob_s1_body)),
            OnboardingSlide(Res.drawable.onboarding_2, BeaconIcons.Camera, stringResource(Res.string.ob_s2_eyebrow), stringResource(Res.string.ob_s2_title), stringResource(Res.string.ob_s2_body)),
            OnboardingSlide(Res.drawable.onboarding_3, BeaconIcons.CloudUp, stringResource(Res.string.ob_s3_eyebrow), stringResource(Res.string.ob_s3_title), stringResource(Res.string.ob_s3_body)),
            OnboardingSlide(Res.drawable.onboarding_4, BeaconIcons.Shield, stringResource(Res.string.ob_s4_eyebrow), stringResource(Res.string.ob_s4_title), stringResource(Res.string.ob_s4_body)),
        )
        val pager = rememberPagerState(pageCount = { slides.size })
        val isLast = pager.currentPage == slides.lastIndex

        fun finish() {
            Prefs.setBool(PrefKeys.ONBOARDING_SEEN, true)
            nav.replaceAll(MainShellScreen)
        }

        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    SlideContent(slides[page])
                }

                // Dots
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(slides.size) { i ->
                        val active = i == pager.currentPage
                        val w by animateDpAsState(if (active) 22.dp else 6.dp, label = "dot")
                        Box(Modifier.height(6.dp).width(w).clip(CircleShape).background(if (active) colors.primary else colors.surface3))
                    }
                }

                BeaconButton(
                    text = if (isLast) stringResource(Res.string.common_get_started) else stringResource(Res.string.common_next),
                    onClick = { if (isLast) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    trailingIcon = if (isLast) BeaconIcons.Check else BeaconIcons.ArrowRight,
                    fullWidth = true,
                    size = BeaconButtonSize.Lg,
                    modifier = Modifier.padding(horizontal = 24.dp).safeBottomPadding().padding(bottom = 20.dp),
                )
            }

            // Skip
            BeaconButton(
                text = stringResource(Res.string.common_skip),
                onClick = { finish() },
                variant = BeaconButtonVariant.Secondary,
                size = BeaconButtonSize.Sm,
                modifier = Modifier.align(Alignment.TopEnd).safeTopPadding().padding(top = 12.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun SlideContent(slide: OnboardingSlide) {
    val colors = BeaconTheme.colors
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            Image(
                painter = painterResource(slide.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Lavender tint + bottom fade into the content sheet
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to colors.primary.copy(alpha = 0.18f),
                        0.55f to Color.Transparent,
                        1.0f to colors.bg,
                    ),
                ),
            )
            // Icon badge
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(86.dp)
                    .beaconPopShadow(RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.surface.copy(alpha = 0.96f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(slide.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
            }
        }

        Column(
            Modifier
                .offset(y = (-28).dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                .padding(horizontal = 28.dp, vertical = 28.dp),
        ) {
            Text(slide.eyebrow.uppercase(), style = BeaconTheme.typography.micro, color = colors.primary)
            Spacer(Modifier.height(12.dp))
            Text(slide.title, style = BeaconTheme.typography.display, color = colors.ink)
            Spacer(Modifier.height(14.dp))
            Text(slide.body, style = BeaconTheme.typography.body, color = colors.ink2, textAlign = TextAlign.Start)
        }
    }
}
