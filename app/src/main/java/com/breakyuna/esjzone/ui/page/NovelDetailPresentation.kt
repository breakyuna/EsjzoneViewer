package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.breakyuna.esjzone.ui.navigation.LocalContainerTransformScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/** A deferred back gesture may keep currentState == targetState while still transforming the page. */
@Composable
internal fun rememberPageTransitionSettled(): State<Boolean> {
    val transition = LocalNavAnimatedContentScope.current.transition
    val shared = LocalContainerTransformScope.current
    return remember(transition, shared) {
        derivedStateOf {
            transition.currentState == EnterExitState.Visible &&
                transition.targetState == EnterExitState.Visible &&
                transition.pendingTargetState == null &&
                !transition.isRunning && shared?.isTransitionActive != true
        }
    }
}

/** Keep the first layout stable until both page motion and cover motion settle,
 * while rendering cached/downloaded results immediately once available.
 */
@Composable
internal fun rememberNovelDetailPresentationState(
    source: StateFlow<NovelPageModel.State>
): State<NovelPageModel.State> {
    val settledState = rememberPageTransitionSettled()
    val initial = remember(source) { source.value }
    return produceState(initial, source, settledState) {
        combine(source, snapshotFlow { settledState.value }) { latest, settled -> latest to settled }
            .collect { (latest, settled) ->
                if (settled || (value !is NovelPageModel.State.Result && latest is NovelPageModel.State.Result)) {
                    value = latest
                }
            }
    }
}
