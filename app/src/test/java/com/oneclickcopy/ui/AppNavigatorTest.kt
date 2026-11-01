package com.oneclickcopy.ui

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppNavigatorTest {

    private val saverScope = SaverScope { true }

    @Test
    fun `opens a document and returns to the list`() {
        val navigator = AppNavigator()

        assertThat(navigator.openEditor(7, isNew = false)).isTrue()
        assertThat(navigator.editor).isEqualTo(EditorRoute(7, isNew = false))

        assertThat(navigator.back()).isTrue()
        assertThat(navigator.editor).isNull()
    }

    @Test
    fun `a double tap on a row opens only the first document`() {
        val navigator = AppNavigator()

        navigator.openEditor(1, isNew = false)
        assertThat(navigator.openEditor(2, isNew = false)).isFalse()

        assertThat(navigator.editor).isEqualTo(EditorRoute(1, isNew = false))
    }

    @Test
    fun `repeated back taps cannot go past the document list`() {
        // The bug Navigation needed a guard for: extra pops emptied the back
        // stack and left a blank screen. Here the list is simply the floor.
        val navigator = AppNavigator()
        navigator.openEditor(1, isNew = true)

        assertThat(navigator.back()).isTrue()
        assertThat(navigator.back()).isFalse()
        assertThat(navigator.back()).isFalse()
        assertThat(navigator.editor).isNull()
    }

    @Test
    fun `saver round-trips the open document`() {
        val navigator = AppNavigator()
        navigator.openEditor(42, isNew = true)

        val saved = with(AppNavigator.Saver) { saverScope.save(navigator) }!!
        val restored = AppNavigator.Saver.restore(saved)!!

        assertThat(restored.editor).isEqualTo(EditorRoute(42, isNew = true))
    }

    @Test
    fun `saver round-trips the document list`() {
        val saved = with(AppNavigator.Saver) { saverScope.save(AppNavigator()) }!!

        assertThat(AppNavigator.Saver.restore(saved)!!.editor).isNull()
    }
}
