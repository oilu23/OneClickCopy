package com.oneclickcopy.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneclickcopy.R
import com.oneclickcopy.ui.common.resolve
import com.oneclickcopy.ui.theme.LocalModeColors
import com.oneclickcopy.ui.theme.OneClickCopyTheme
import com.oneclickcopy.ui.util.ClipboardHelper
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val copiedMessage = stringResource(R.string.editor_copied_toast)
    val resetMessage = stringResource(R.string.editor_checks_reset)
    val errorText = (event as? EditorEvent.Error)?.message?.resolve()

    // Flush pending edits when the screen leaves composition, so the last
    // keystrokes are never lost to the debounce window.
    DisposableEffect(Unit) {
        onDispose { viewModel.saveNow() }
    }

    LaunchedEffect(event) {
        when (val current = event) {
            is EditorEvent.Copied -> {
                if (ClipboardHelper.copiedFeedbackNeeded) {
                    snackbarHostState.showSnackbar(copiedMessage)
                }
                viewModel.consumeEvent()
            }
            is EditorEvent.ChecksReset -> {
                snackbarHostState.showSnackbar(resetMessage)
                viewModel.consumeEvent()
            }
            is EditorEvent.Error -> {
                snackbarHostState.showSnackbar(errorText.orEmpty())
                viewModel.consumeEvent()
            }
            null -> Unit
        }
    }

    if (!uiState.documentExists && !uiState.isLoading) {
        LaunchedEffect(Unit) { onNavigateBack() }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (uiState.title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.editor_title_hint),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    // Reset lives beside the mode toggle rather than in a bottom
                    // bar, where it sat directly above the system navigation
                    // buttons and was easy to mis-tap.
                    if (uiState.isCopyMode && uiState.totalCount > 0) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onResetChecks()
                            },
                            enabled = uiState.copiedCount > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.editor_reset_checks),
                                tint = if (uiState.copiedCount > 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        }
                    }
                    ModeToggle(
                        isCopyMode = uiState.isCopyMode,
                        onToggle = {
                            keyboard?.hide()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onToggleMode()
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (uiState.isCopyMode && uiState.totalCount > 0) {
                EditorProgressBar(
                    copied = uiState.copiedCount,
                    total = uiState.totalCount,
                    progress = uiState.progress,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                uiState.isCopyMode -> {
                    CopyModeList(
                        uiState = uiState,
                        onCopy = { snippet ->
                            ClipboardHelper.copy(context, snippet.text)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onSnippetCopied(snippet)
                        },
                        onToggleChecked = { snippet ->
                            if (snippet.isCopied) {
                                viewModel.onSnippetUnchecked(snippet)
                            } else {
                                viewModel.onSnippetCopied(snippet)
                            }
                        },
                        onReorder = viewModel::onReorder,
                    )
                }
                else -> {
                    EditModeField(
                        rawText = uiState.rawText,
                        onTextChanged = viewModel::onTextChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyModeList(
    uiState: EditorUiState,
    onCopy: (com.oneclickcopy.domain.Snippet) -> Unit,
    onToggleChecked: (com.oneclickcopy.domain.Snippet) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    if (uiState.snippets.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.editor_empty_copy_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.editor_empty_copy_mode_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onReorder(from.index, to.index)
    }

    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        // Extra bottom padding so the final row can always be scrolled clear of
        // the system navigation bar / gesture area.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = uiState.snippets.size,
            key = { index -> uiState.snippets[index].key.encode() },
        ) { index ->
            val snippet = uiState.snippets[index]
            ReorderableItem(reorderState, key = snippet.key.encode()) { isDragging ->
                SnippetRow(
                    snippet = snippet,
                    isDragging = isDragging,
                    onCopy = { onCopy(snippet) },
                    onToggleChecked = { onToggleChecked(snippet) },
                    dragHandle = {
                        SnippetDragHandle(
                            modifier = Modifier.draggableHandle(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun EditModeField(
    rawText: String,
    onTextChanged: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding(),
    ) {
        BasicTextField(
            value = rawText,
            onValueChange = onTextChanged,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (rawText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_text_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Composable
private fun ModeToggle(
    isCopyMode: Boolean,
    onToggle: () -> Unit,
) {
    val modeColors = LocalModeColors.current
    val containerColor by animateColorAsState(
        targetValue = if (isCopyMode) modeColors.copyMode else modeColors.editMode,
        label = "modeToggle",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                onClick = onToggle,
                onClickLabel = stringResource(
                    if (isCopyMode) R.string.editor_switch_to_edit
                    else R.string.editor_switch_to_copy
                ),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isCopyMode) Icons.Default.TouchApp else Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isCopyMode) R.string.editor_mode_copy else R.string.editor_mode_edit
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Slim progress strip shown at the bottom in copy mode.
 *
 * Contains no tap targets: the reset action moved to the top bar because a
 * button here sits directly above the system navigation buttons/gesture bar and
 * was easy to mis-tap. [navigationBarsPadding] keeps the text clear of the
 * system bars under edge-to-edge.
 */
@Composable
private fun EditorProgressBar(
    copied: Int,
    total: Int,
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "copyProgress",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (copied == total) {
                    stringResource(R.string.editor_all_copied)
                } else {
                    stringResource(R.string.editor_progress, copied, total)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}
