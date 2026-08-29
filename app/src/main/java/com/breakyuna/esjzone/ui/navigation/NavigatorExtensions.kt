package com.breakyuna.esjzone.ui.navigation

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

/**
 * Navigates to a destination without adding a second screen with the same key.
 *
 * Voyager keeps the previous and next screens composed while a transition is
 * running. Reusing an existing screen also prevents duplicate transition state
 * providers and duplicate initial network requests after A -> B -> A flows.
 */
fun Navigator.pushIfNotCurrent(screen: Screen): Boolean {
    val existingIndex = items.indexOfLast { it.key == screen.key }
    if (existingIndex == items.lastIndex) {
        return false
    }
    if (existingIndex >= 0) {
        popUntil { it.key == screen.key }
        return false
    }

    push(screen)
    return true
}
