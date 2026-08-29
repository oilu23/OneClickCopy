package com.oneclickcopy.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneclickcopy.R
import com.oneclickcopy.ui.BackNavigationGate
import com.oneclickcopy.ui.common.resolve
import com.oneclickcopy.ui.theme.LocalModeColors
import com.oneclickcopy.ui.util.ClipboardHelper
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Boolean,
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
        when (event) {
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

    // The gate latches only after Navigation accepts the pop. Right after this
    // screen opens the destination can still be STARTED; popBackStackOnce then
    // correctly returns false, and Back must stay enabled for a retry.
    val exitGate = remember { BackNavigationGate() }
    val leaveEditor: () -> Unit = { exitGate.tryLeave(onNavigateBack) }

    // Intercept the system back gesture so it uses the same guarded path.
    BackHandler(enabled = !exitGate.isLeaving) { leaveEditor() }

    // A missing document (deleted elsewhere, or a stale link) sends the user back.
    LaunchedEffect(uiState.documentExists, uiState.isLoading) {
        if (!uiState.documentExists && !uiState.isLoading) {
            leaveEditor()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
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
                    IconButton(onClick = leaveEditor, enabled = !exitGate.isLeaving) {
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
                                viewModel.onUndoReorder()
                            },
                            enabled = uiState.canUndoReorder,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.editor_undo_reorder),
                                tint = if (uiState.canUndoReorder) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        }
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
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
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
                        onEdit = viewModel::onSnippetLongPress,
                        onPaste = viewModel::onSnippetPasteAfter,
                        onDelete = viewModel::onSnippetDelete,
                        canPaste = uiState.canPaste,
                    )
                }
                else -> {
                    EditModeField(
                        rawText = uiState.rawText,
                        editRequestedOffset = uiState.editRequestedOffset,
                        onTextChanged = viewModel::onTextChanged,
                        onEditRequestApplied = viewModel::consumeEditRequest,
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
    onEdit: (com.oneclickcopy.domain.Snippet) -> Unit,
    onPaste: (com.oneclickcopy.domain.Snippet) -> Unit,
    onDelete: (com.oneclickcopy.domain.Snippet) -> Unit,
    canPaste: Boolean,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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

    LazyColumn(
        state = listState,
        // Extra bottom padding so the final row can always be scrolled clear of
        // the system navigation bar / gesture area.
        contentPadding = PaddingValues(
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
            val menuKey = snippet.key.encode()
            var menuOpen by remember { mutableStateOf(false) }
            ReorderableItem(reorderState, key = menuKey) { isDragging ->
                Box {
                    SnippetRow(
                        snippet = snippet,
                        isDragging = isDragging,
                        onCopy = { onCopy(snippet) },
                        onToggleChecked = { onToggleChecked(snippet) },
                        onLongPress = { menuOpen = true },
                        dragHandle = {
                            SnippetDragHandle(
                                modifier = Modifier.draggableHandle(),
                            )
                        },
                    )
                    SnippetContextMenu(
                        expanded = menuOpen,
                        canPaste = canPaste,
                        onDismiss = { menuOpen = false },
                        onEdit = { menuOpen = false; onEdit(snippet) },
                        onCopy = { menuOpen = false; onCopy(snippet) },
                        onPaste = { menuOpen = false; onPaste(snippet) },
                        onDelete = { menuOpen = false; onDelete(snippet) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SnippetContextMenu(
    expanded: Boolean,
    canPaste: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.snippet_action_edit)) },
            onClick = onEdit,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.snippet_action_copy)) },
            onClick = onCopy,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.snippet_action_paste)) },
            onClick = onPaste,
            enabled = canPaste,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.snippet_action_delete)) },
            onClick = onDelete,
        )
    }
}

@Composable
private fun EditModeField(
    rawText: String,
    editRequestedOffset: Int?,
    onTextChanged: (String) -> Unit,
    onEditRequestApplied: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The selection is tracked locally so the cursor does not reset when the
    // text is re-synced from the ViewModel on every keystroke.
    var lastSelection by remember { mutableStateOf<TextRange?>(null) }
    val value = TextFieldValue(
        text = rawText,
        selection = editRequestedOffset
            ?.let { TextRange(it.coerceIn(0, rawText.length)) }
            ?: lastSelection?.takeIf { it.min <= rawText.length }
            ?: TextRange(0),
    )
    val originalOffset = editRequestedOffset

    // When a row long-press handed us a target cursor position, focus the field
    // and raise the keyboard so the user can immediately add or edit text. The
    // offset is captured into the local selection first so the cursor stays put
    // after the one-shot request is consumed.
    LaunchedEffect(originalOffset) {
        if (originalOffset != null) {
            lastSelection = TextRange(originalOffset.coerceIn(0, rawText.length))
            focusRequester.requestFocus()
            keyboard?.show()
            onEditRequestApplied()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding(),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                lastSelection = newValue.selection
                onTextChanged(newValue.text)
            },
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
                .padding(16.dp)
                .focusRequester(focusRequester),
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
    val contentColor by animateColorAsState(
        targetValue = if (isCopyMode) modeColors.onCopyMode else modeColors.onEditMode,
        label = "modeToggleContent",
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
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isCopyMode) R.string.editor_mode_copy else R.string.editor_mode_edit
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
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
