package com.stepanok.undp.designsystem.labels

import androidx.compose.runtime.Composable
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.SyncState
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.crisis_chemical
import undp.shared.generated.resources.crisis_conflict
import undp.shared.generated.resources.crisis_earthquake
import undp.shared.generated.resources.crisis_explosion
import undp.shared.generated.resources.crisis_flood
import undp.shared.generated.resources.crisis_hurricane
import undp.shared.generated.resources.crisis_tsunami
import undp.shared.generated.resources.crisis_unrest
import undp.shared.generated.resources.crisis_wildfire
import undp.shared.generated.resources.damage_none
import undp.shared.generated.resources.damage_slight
import undp.shared.generated.resources.damage_moderate
import undp.shared.generated.resources.damage_severe
import undp.shared.generated.resources.damage_destroyed
import undp.shared.generated.resources.debris_no
import undp.shared.generated.resources.debris_unsure
import undp.shared.generated.resources.debris_yes
import undp.shared.generated.resources.infra_commercial
import undp.shared.generated.resources.infra_community
import undp.shared.generated.resources.infra_government
import undp.shared.generated.resources.infra_other
import undp.shared.generated.resources.infra_public
import undp.shared.generated.resources.infra_residential
import undp.shared.generated.resources.infra_transport
import undp.shared.generated.resources.infra_utility
import undp.shared.generated.resources.reject_duplicate
import undp.shared.generated.resources.reject_invalid
import undp.shared.generated.resources.reject_rate_limited

@Composable
fun damageLabel(level: DamageLevel): String = stringResource(
    when (level) {
        DamageLevel.NONE -> Res.string.damage_none
        DamageLevel.SLIGHT -> Res.string.damage_slight
        DamageLevel.MODERATE -> Res.string.damage_moderate
        DamageLevel.SEVERE -> Res.string.damage_severe
        DamageLevel.DESTROYED -> Res.string.damage_destroyed
    },
)

@Composable
fun infraLabel(type: InfraType): String = stringResource(
    when (type) {
        InfraType.RESIDENTIAL -> Res.string.infra_residential
        InfraType.COMMERCIAL -> Res.string.infra_commercial
        InfraType.GOVERNMENT -> Res.string.infra_government
        InfraType.UTILITY -> Res.string.infra_utility
        InfraType.TRANSPORT -> Res.string.infra_transport
        InfraType.COMMUNITY -> Res.string.infra_community
        InfraType.PUBLIC -> Res.string.infra_public
        InfraType.OTHER -> Res.string.infra_other
    },
)

@Composable
fun crisisLabel(nature: CrisisNature): String = stringResource(
    when (nature) {
        CrisisNature.EARTHQUAKE -> Res.string.crisis_earthquake
        CrisisNature.FLOOD -> Res.string.crisis_flood
        CrisisNature.TSUNAMI -> Res.string.crisis_tsunami
        CrisisNature.HURRICANE -> Res.string.crisis_hurricane
        CrisisNature.WILDFIRE -> Res.string.crisis_wildfire
        CrisisNature.EXPLOSION -> Res.string.crisis_explosion
        CrisisNature.CHEMICAL -> Res.string.crisis_chemical
        CrisisNature.CONFLICT -> Res.string.crisis_conflict
        CrisisNature.CIVIL_UNREST -> Res.string.crisis_unrest
    },
)

@Composable
fun debrisLabel(debris: DebrisState): String = stringResource(
    when (debris) {
        DebrisState.YES -> Res.string.debris_yes
        DebrisState.NO -> Res.string.debris_no
        DebrisState.UNSURE -> Res.string.debris_unsure
    },
)

/** Human reason for a terminal server rejection: the common envelope codes get a localized
 *  line; anything else falls back to the server's own (English) message. */
@Composable
fun rejectionReasonLabel(state: SyncState.Rejected): String = when (state.code) {
    "duplicate" -> stringResource(Res.string.reject_duplicate)
    "rate_limited" -> stringResource(Res.string.reject_rate_limited)
    "validation" -> stringResource(Res.string.reject_invalid)
    else -> state.reason
}
