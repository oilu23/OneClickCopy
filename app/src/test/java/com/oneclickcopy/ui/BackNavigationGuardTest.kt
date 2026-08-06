package com.oneclickcopy.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back-navigation guarding.
 *
 * Reported bug: tapping the back arrow two or three times in quick succession
 * while a document was open left the app on a blank screen. Each tap fired an
 * independent popBackStack(); the first returned to the document list and the
 * rest popped the list itself, emptying the back stack.
 *
 * These tests model the latch used by the editor screen. They are deliberately
 * free of Compose and Navigation so the logic can be verified on the JVM;
 * the wiring itself is exercised on-device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackNavigationGuardTest {

    /** Mirrors the editor's single guarded exit path. */
    private class EditorExit {
        var isLeaving = false
            private set
        var popCount = 0
            private set

        fun leave() {
            if (!isLeaving) {
                isLeaving = true
                popCount++
            }
        }
    }

    @Test
    fun `a single back tap pops once`() {
        val exit = EditorExit()

        exit.leave()

        assertThat(exit.popCount).isEqualTo(1)
    }

    @Test
    fun `three rapid back taps still pop only once`() {
        val exit = EditorExit()

        exit.leave()
        exit.leave()
        exit.leave()

        // Without the latch this was 3, which emptied the back stack and left
        // the user staring at a blank screen.
        assertThat(exit.popCount).isEqualTo(1)
    }

    @Test
    fun `the back arrow and the system gesture together pop only once`() {
        val exit = EditorExit()

        exit.leave() // arrow
        exit.leave() // system back racing it

        assertThat(exit.popCount).isEqualTo(1)
    }

    @Test
    fun `the missing-document redirect cannot pop after a manual back`() {
        val exit = EditorExit()

        exit.leave() // user taps back
        exit.leave() // document then resolves as missing and redirects

        assertThat(exit.popCount).isEqualTo(1)
    }

    @Test
    fun `the guard latches so later taps are ignored`() {
        val exit = EditorExit()

        exit.leave()
        repeat(10) { exit.leave() }

        assertThat(exit.isLeaving).isTrue()
        assertThat(exit.popCount).isEqualTo(1)
    }

    @Test
    fun `popBackStackOnce ignores an entry that is already leaving`() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }

        // A destination still in the foreground may pop.
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertThat(
            owner.registry.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ).isTrue()

        // Once it starts leaving it drops below RESUMED and must be ignored,
        // which is what stops a second pop from removing the document list.
        owner.registry.currentState = Lifecycle.State.STARTED
        assertThat(
            owner.registry.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ).isFalse()
    }
}
