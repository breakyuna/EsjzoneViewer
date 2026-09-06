package com.breakyuna.esjzone.ui.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.breakyuna.esjzone.novellibrary.novel.Chapter

/**
 * Keeps a Compose state linked while the destination is alive. Navigation 3
 * persists the typed route, not this presentation holder; reader progress is
 * persisted by the existing domain/repository contract.
 */
class ChapterStateHolder(initialValue: Chapter? = null) {
    private var linkedState: MutableState<Chapter?>? = mutableStateOf(initialValue)

    fun state(): MutableState<Chapter?> {
        return linkedState ?: mutableStateOf<Chapter?>(null).also { linkedState = it }
    }
}

/** See [ChapterStateHolder] for why this is not a serializable screen state. */
class BooleanStateHolder(initialValue: Boolean = false) {
    private var linkedState: MutableState<Boolean>? = mutableStateOf(initialValue)

    fun state(): MutableState<Boolean> {
        return linkedState ?: mutableStateOf(false).also { linkedState = it }
    }
}
