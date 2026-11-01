package com.oneclickcopy.ui.home

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.oneclickcopy.R
import com.oneclickcopy.ui.util.ClipboardHelper

/**
 * Shows the GitHub device-flow code while the app waits for approval.
 *
 * The user copies the code, approves it on github.com, and returns; the dialog
 * closes by itself once the token arrives. Tapping outside does not dismiss it,
 * so a stray tap cannot abandon a sign-in the user is midway through.
 */
@Composable
fun GitHubSignInDialog(
    state: GitHubSignInState,
    onCancel: () -> Unit,
) {
    if (state is GitHubSignInState.Hidden) return

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val copiedMessage = stringResource(R.string.github_sign_in_code_copied)

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.github_sign_in_title)) },
        text = {
            when (state) {
                is GitHubSignInState.AwaitingApproval -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.github_sign_in_instructions))
                    Text(
                        text = state.code.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = {
                            ClipboardHelper.copy(context, state.code.userCode)
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            uriHandler.openUri(state.code.verificationUri)
                        },
                    ) {
                        Text(stringResource(R.string.github_sign_in_open))
                    }
                    WaitingRow(stringResource(R.string.github_sign_in_waiting))
                }
                else -> WaitingRow(stringResource(R.string.github_sign_in_requesting))
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.github_sign_in_cancel))
            }
        },
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
