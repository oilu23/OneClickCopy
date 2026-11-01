package com.oneclickcopy.desktop.ui.home

import com.oneclickcopy.ui.icons.AppIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oneclickcopy.desktop.ui.Strings
import com.oneclickcopy.desktop.ui.Tooltip
import com.oneclickcopy.desktop.ui.formatRelativeTime
import com.oneclickcopy.sync.SyncState

/** Compact sync indicator; hover shows when the last backup happened. */
@Composable
fun SyncStatusChip(state: SyncState, modifier: Modifier = Modifier) {
    val (icon, label) = when (state) {
        is SyncState.SignedOut -> AppIcons.CloudOff to Strings.SYNC_SIGNED_OUT
        is SyncState.Idle -> AppIcons.CloudDone to Strings.SYNC_UP_TO_DATE
        is SyncState.Pending -> AppIcons.CloudQueue to Strings.SYNC_PENDING
        is SyncState.Syncing -> AppIcons.CloudSync to Strings.SYNC_IN_PROGRESS
        is SyncState.WaitingForNetwork -> AppIcons.CloudOff to Strings.SYNC_WAITING_NETWORK
        is SyncState.Failed -> AppIcons.ErrorOutline to Strings.SYNC_FAILED_SHORT
    }
    val tint by animateColorAsState(
        targetValue = when (state) {
            is SyncState.Idle -> MaterialTheme.colorScheme.primary
            is SyncState.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "syncTint",
    )
    val detail = when {
        state is SyncState.Idle && state.lastSyncedAt > 0 -> Strings.syncLastSynced(formatRelativeTime(state.lastSyncedAt))
        state is SyncState.Failed && state.message.isNotBlank() -> state.message
        else -> label
    }

    Tooltip(detail) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            if (state is SyncState.Syncing) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = tint, modifier = Modifier.size(16.dp))
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
