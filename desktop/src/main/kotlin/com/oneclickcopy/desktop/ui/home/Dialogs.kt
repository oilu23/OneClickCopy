package com.oneclickcopy.desktop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.desktop.ui.Strings

/** Confirms deletion of a document, naming it and its item count. */
@Composable
fun DeleteDocumentDialog(
    document: DocumentEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val itemCount = remember(document.content) { document.content.lineSequence().count { it.isNotBlank() } }
    val title = document.title.ifBlank { Strings.DOCUMENT_UNTITLED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.HOME_DELETE_CONFIRM_TITLE) },
        text = {
            Text(
                if (itemCount > 0) Strings.homeDeleteConfirmMessage(title, itemCount)
                else Strings.homeDeleteConfirmMessageEmpty(title)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(Strings.HOME_DELETE_CONFIRM_ACTION, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/** Shows the GitHub device-flow code while the app waits for approval. */
@Composable
fun GitHubSignInDialog(state: GitHubSignInState, onCancel: () -> Unit) {
    if (state is GitHubSignInState.Hidden) return
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(Strings.GITHUB_SIGN_IN_TITLE) },
        text = {
            when (state) {
                is GitHubSignInState.AwaitingApproval -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(Strings.GITHUB_SIGN_IN_INSTRUCTIONS)
                    Text(
                        text = state.code.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(state.code.userCode))
                        uriHandler.openUri(state.code.verificationUri)
                    }) {
                        Text(Strings.GITHUB_SIGN_IN_OPEN)
                    }
                    WaitingRow(Strings.GITHUB_SIGN_IN_WAITING)
                }
                else -> WaitingRow(Strings.GITHUB_SIGN_IN_REQUESTING)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun WaitingRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
