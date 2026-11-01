package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.breakyuna.esjzone.ui.navigation.LocalContainerTransformScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/** True once both the page motion and the shared cover/container motion have settled. */
@Composable
internal fun rememberNovelDetailTransitionSettled(): State<Boolean> {
    val transition = LocalNavAnimatedContentScope.current.transition
    val shared = LocalContainerTransformScope.current
    return remember(transition, shared) {
        derivedStateOf {
            transition.currentState == EnterExitState.Visible &&
                transition.targetState == EnterExitState.Visible &&
                !transition.isRunning && shared?.isTransitionActive != true
        }
    }
}

/** Keep the first layout stable until both page motion and cover motion settle.
 * Loading continues in the model; only presentation of its latest result is deferred.
 */
@Composable
internal fun rememberNovelDetailPresentationState(
    source: StateFlow<NovelPageModel.State>
): State<NovelPageModel.State> {
    val settledState = rememberNovelDetailTransitionSettled()
    val current = source.collectAsState()
    val initial = remember(source) { current.value }
    return produceState(initial, source, settledState) {
        combine(source, snapshotFlow { settledState.value }) { latest, settled -> latest to settled }
            .collect { (latest, settled) ->
                if (settled) value = latest
            }
    }
}
