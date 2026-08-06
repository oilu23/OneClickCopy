package com.oneclickcopy.ui

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Guards a back navigation so it runs at most once per destination visit.
 *
 * Tapping the back arrow several times quickly, or the arrow racing the system
 * back gesture, previously fired popBackStack() once per tap. The first pop
 * returns to the document list; each extra pop removes the list as well, leaving
 * an empty back stack and a blank screen.
 *
 * Checking the entry's lifecycle state is what makes this reliable: a destination
 * that is already leaving is no longer RESUMED, so any further pop requests for
 * it are ignored regardless of how they were triggered.
 */
fun NavController.popBackStackOnce(entry: NavBackStackEntry): Boolean {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return popBackStack()
    }
    return false
}
