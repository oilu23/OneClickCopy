package com.oneclickcopy.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.oneclickcopy.R
import com.oneclickcopy.sync.SyncState
import com.oneclickcopy.ui.util.formatRelativeTime

/**
 * Compact sync indicator.
 *
 * Exists so the user can confirm at a glance that their data is safe, rather
 * than having to trust an invisible background process.
 */
@Composable
fun SyncStatusChip(
    state: SyncState,
    modifier: Modifier = Modifier,
) {
    val (icon, labelRes) = when (state) {
        is SyncState.SignedOut -> Icons.Default.CloudOff to R.string.sync_signed_out
        is SyncState.Idle -> Icons.Default.CloudDone to R.string.sync_up_to_date
        is SyncState.Pending -> Icons.Default.CloudQueue to R.string.sync_pending
        is SyncState.Syncing -> Icons.Default.CloudSync to R.string.sync_in_progress
        is SyncState.WaitingForNetwork -> Icons.Default.CloudOff to R.string.sync_waiting_network
        is SyncState.Failed -> Icons.Default.ErrorOutline to R.string.sync_failed_short
    }

    val tint by animateColorAsState(
        targetValue = when (state) {
            is SyncState.Idle -> MaterialTheme.colorScheme.primary
            is SyncState.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "syncTint",
    )

    val label = stringResource(labelRes)
    val detail = if (state is SyncState.Idle && state.lastSyncedAt > 0) {
        stringResource(R.string.sync_last_synced, formatRelativeTime(state.lastSyncedAt))
    } else {
        label
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = detail },
    ) {
        if (state is SyncState.Syncing) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = tint,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}
