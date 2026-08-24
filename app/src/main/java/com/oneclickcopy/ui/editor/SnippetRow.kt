package com.oneclickcopy.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickcopy.R
import com.oneclickcopy.domain.Snippet
import com.oneclickcopy.ui.theme.OneClickCopyTheme

/**
 * A single tappable copy row.
 *
 * Accessibility: the whole row is one node with an explicit click label and a
 * state description, so TalkBack announces "Copy <text>, copied" instead of the
 * original's three unlabelled children.
 */
@Composable
fun SnippetRow(
    snippet: Snippet,
    isDragging: Boolean,
    onCopy: () -> Unit,
    onToggleChecked: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit = {},
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
            snippet.isCopied -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "snippetContainer",
    )

    val contentAlpha = if (snippet.isCopied) 0.55f else 1f
    val copyLabel = stringResource(R.string.snippet_copy_action, snippet.text)
    val stateLabel = stringResource(
        if (snippet.isCopied) R.string.snippet_copied else R.string.snippet_not_copied
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isDragging) 6.dp else 0.dp,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = copyLabel
                stateDescription = stateLabel
                onClick(label = "copy") { onCopy(); true }
            },
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onCopy)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dragHandle()

            Text(
                text = snippet.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )

            Icon(
                imageVector = if (snippet.isCopied) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (snippet.isCopied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleChecked),
            )
        }
    }
}

@Composable
fun SnippetDragHandle(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.DragIndicator,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.size(24.dp),
    )
}

@Preview
@Composable
private fun SnippetRowPreview() {
    OneClickCopyTheme {
        SnippetRow(
            snippet = Snippet("Thanks for reaching out!", 0, 0, isCopied = true),
            isDragging = false,
            onCopy = {},
            onToggleChecked = {},
            dragHandle = { SnippetDragHandle() },
        )
    }
}
