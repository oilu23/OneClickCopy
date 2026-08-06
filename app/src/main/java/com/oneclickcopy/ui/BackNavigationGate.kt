package com.oneclickcopy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One-shot gate for a screen exit.
 *
 * Navigation may legitimately reject an early pop while a destination is still
 * entering (its lifecycle has not reached RESUMED). In that case the gate must
 * remain open: disabling Back before a pop succeeds strands the user on the
 * editor with a permanently greyed-out arrow.
 */
class BackNavigationGate {
    var isLeaving: Boolean by mutableStateOf(false)
        private set

    /** Runs [navigate] once. Latches only after Navigation accepted the pop. */
    fun tryLeave(navigate: () -> Boolean) {
        if (!isLeaving && navigate()) {
            isLeaving = true
        }
    }
}
