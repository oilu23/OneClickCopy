package com.oneclickcopy.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.ui.theme.OneClickCopyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Delete confirmation behaviour.
 *
 * Deleting removes every snippet in a document, so the destructive action must
 * require an explicit second step. These tests assert that dismissing the dialog
 * leaves the document alone and only the confirm button deletes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class DeleteDocumentDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val document = DocumentEntity(
        id = 1,
        title = "Ubuntu setup",
        content = "sudo apt update\nsudo apt upgrade\nreboot",
    )

    @Test
    fun `dialog shows the document title and item count`() {
        composeRule.setContent {
            OneClickCopyTheme(darkTheme = true, dynamicColor = false) {
                DeleteDocumentDialog(document = document, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Delete document?").assertIsDisplayed()
        // Naming the document guards against deleting the wrong row.
        composeRule.onNodeWithText("\"Ubuntu setup\" and its 3 items will be deleted.")
            .assertIsDisplayed()
    }

    @Test
    fun `confirming reports the deletion exactly once`() {
        var confirmed = 0
        composeRule.setContent {
            OneClickCopyTheme(darkTheme = true, dynamicColor = false) {
                DeleteDocumentDialog(
                    document = document,
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Delete").performClick()

        assertThat(confirmed).isEqualTo(1)
    }

    @Test
    fun `cancelling does not delete`() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            OneClickCopyTheme(darkTheme = true, dynamicColor = false) {
                DeleteDocumentDialog(
                    document = document,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(confirmed).isEqualTo(0)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `an untitled document is described without an empty name`() {
        composeRule.setContent {
            OneClickCopyTheme(darkTheme = true, dynamicColor = false) {
                DeleteDocumentDialog(
                    document = DocumentEntity(id = 2, title = "", content = "one"),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("\"Untitled\" and its 1 items will be deleted.")
            .assertIsDisplayed()
    }

    @Test
    fun `an empty document omits the item count`() {
        composeRule.setContent {
            OneClickCopyTheme(darkTheme = true, dynamicColor = false) {
                DeleteDocumentDialog(
                    document = DocumentEntity(id = 3, title = "Empty", content = ""),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("\"Empty\" will be deleted.").assertIsDisplayed()
    }
}
