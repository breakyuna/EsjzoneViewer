package com.breakyuna.esjzone.ui.designsystem


import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.breakyuna.esjzone.R

/** First-party animation assets with a deterministic static fallback. */
enum class AppLottieAsset(
    @RawRes val rawRes: Int,
    val fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val loops: Boolean
) {
    EmptyState(R.raw.lottie_empty_state, Icons.Outlined.Inbox, loops = true),
    Completion(R.raw.lottie_completion, Icons.Outlined.CheckCircle, loops = false)
}

/**
 * Renders an app-owned Lottie asset, falling back to a Material icon while the
 * composition loads or whenever Android's animation scale is disabled.
 *
 * The JSON assets in res/raw are intentionally tiny and authored in-repository;
 * no downloaded or unknown-license animation is bundled.
 */
@Composable
fun AppLottieState(
    asset: AppLottieAsset,
    modifier: Modifier = Modifier.size(80.dp),
    contentDescription: String? = null
) {
    val reduceMotion = !rememberMotionActive()
    val accessibleModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }

    if (reduceMotion) {
        Icon(
            imageVector = asset.fallbackIcon,
            contentDescription = contentDescription,
            modifier = accessibleModifier
        )
        return
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(asset.rawRes)
    )
    if (composition == null) {
        Icon(
            imageVector = asset.fallbackIcon,
            contentDescription = contentDescription,
            modifier = accessibleModifier
        )
    } else {
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = if (asset.loops) LottieConstants.IterateForever else 1
        )
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = accessibleModifier
        )
    }
}
