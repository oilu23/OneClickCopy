package com.oneclickcopy.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression coverage for navigation before the editor destination has reached
 * RESUMED. This is possible when a user taps a document and immediately taps
 * Back while the enter transition is still running.
 */
class BackNavigationGateTest {

    @Test
    fun `failed early pop leaves back enabled for a later retry`() {
        val gate = BackNavigationGate()
        var popAttempts = 0

        // Navigation correctly refuses a pop while the new destination has not
        // reached RESUMED yet. This must not permanently latch the UI as leaving.
        gate.tryLeave {
            popAttempts++
            false
        }

        assertThat(gate.isLeaving).isFalse()

        // Once the destination is ready, the next tap must work.
        gate.tryLeave {
            popAttempts++
            true
        }

        assertThat(popAttempts).isEqualTo(2)
        assertThat(gate.isLeaving).isTrue()
    }

    @Test
    fun `a successful pop blocks repeated taps`() {
        val gate = BackNavigationGate()
        var popAttempts = 0

        repeat(3) {
            gate.tryLeave {
                popAttempts++
                true
            }
        }

        assertThat(popAttempts).isEqualTo(1)
        assertThat(gate.isLeaving).isTrue()
    }
}
