package com.oneclickcopy.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.oneclickcopy.R
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.ui.theme.OneClickCopyTheme

/**
 * Confirms deletion of a document.
 *
 * Deleting removes every snippet in the document, so it is worth an explicit
 * confirmation rather than relying solely on the undo snackbar, which
 * disappears after a few seconds and is easy to miss.
 *
 * The dialog names the document and its item count so the user can tell at a
 * glance whether they opened the menu on the row they meant to.
 */
@Composable
fun DeleteDocumentDialog(
    document: DocumentEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val itemCount = remember(document.content) {
        document.content.lineSequence().count { it.isNotBlank() }
    }
    val title = document.title.ifBlank { stringResource(R.string.document_untitled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_delete_confirm_title)) },
        text = {
            Text(
                if (itemCount > 0) {
                    stringResource(R.string.home_delete_confirm_message, title, itemCount)
                } else {
                    stringResource(R.string.home_delete_confirm_message_empty, title)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.home_delete_confirm_action),
                    // Destructive actions are coloured as such so the confirm
                    // button is not mistaken for the safe choice.
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_delete_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun DeleteDocumentDialogPreview() {
    OneClickCopyTheme(darkTheme = true, dynamicColor = false) {
        DeleteDocumentDialog(
            document = DocumentEntity(
                id = 1,
                title = "Ubuntu setup",
                content = "sudo apt update\nsudo apt upgrade\nreboot",
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
