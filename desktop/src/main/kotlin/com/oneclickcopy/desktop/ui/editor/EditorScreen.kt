package com.oneclickcopy.desktop.ui.editor

import com.oneclickcopy.ui.icons.AppIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.oneclickcopy.desktop.ui.KeyShortcutHandler
import com.oneclickcopy.desktop.ui.Strings
import com.oneclickcopy.desktop.ui.Tooltip
import com.oneclickcopy.desktop.ui.home.PinButton
import com.oneclickcopy.desktop.ui.theme.LocalModeColors
import com.oneclickcopy.domain.Snippet
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    alwaysOnTop: Boolean,
    onToggleAlwaysOnTop: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val copy: (Snippet) -> Unit = { snippet ->
        clipboard.setText(AnnotatedString(snippet.text))
        viewModel.onSnippetCopied(snippet)
    }
    val toggleMode = {
        focusManager.clearFocus()
        viewModel.onToggleMode()
    }

    KeyShortcutHandler { key ->
        when {
            key.key == Key.Escape -> { onNavigateBack(); true }
            key.isCtrlPressed && key.key == Key.E -> { toggleMode(); true }
            // Ctrl+Z belongs to the text field in edit mode.
            uiState.isCopyMode && key.isCtrlPressed && !key.isShiftPressed && key.key == Key.Z -> {
                viewModel.onUndoReorder(); true
            }
            // Enter copies the next unchecked row, so a list can be worked
            // through from the keyboard: Enter, Alt+Tab, Ctrl+V, Alt+Tab.
            uiState.isCopyMode && (key.key == Key.Enter || key.key == Key.NumPadEnter) -> {
                uiState.snippets.firstOrNull { !it.isCopied }?.let(copy)
                true
            }
            else -> false
        }
    }

    // Snackbars run in their own scope: consuming the event clears it, which
    // would cancel a snackbar shown from inside the LaunchedEffect itself.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(event) {
        val text = when (val current = event) {
            is EditorEvent.Copied -> "${Strings.EDITOR_COPIED}: ${current.text}"
            is EditorEvent.ChecksReset -> Strings.EDITOR_CHECKS_RESET
            is EditorEvent.Error -> current.message
            null -> return@LaunchedEffect
        }
        viewModel.consumeEvent()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarScope.launch { snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(uiState.documentExists, uiState.isLoading) {
        if (!uiState.documentExists && !uiState.isLoading) onNavigateBack()
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
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (uiState.title.isEmpty()) {
                                Text(
                                    Strings.EDITOR_TITLE_HINT,
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
                    Tooltip(Strings.EDITOR_BACK) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(AppIcons.ArrowBack, contentDescription = Strings.EDITOR_BACK)
                        }
                    }
                },
                actions = {
                    if (uiState.isCopyMode && uiState.totalCount > 0) {
                        Tooltip(Strings.EDITOR_UNDO_REORDER) {
                            IconButton(onClick = viewModel::onUndoReorder, enabled = uiState.canUndoReorder) {
                                Icon(AppIcons.Undo, contentDescription = Strings.EDITOR_UNDO_REORDER)
                            }
                        }
                        Tooltip(Strings.EDITOR_RESET_CHECKS) {
                            IconButton(onClick = viewModel::onResetChecks, enabled = uiState.copiedCount > 0) {
                                Icon(AppIcons.Refresh, contentDescription = Strings.EDITOR_RESET_CHECKS)
                            }
                        }
                    }
                    PinButton(alwaysOnTop, onToggleAlwaysOnTop)
                    ModeToggle(isCopyMode = uiState.isCopyMode, onToggle = toggleMode)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (uiState.isCopyMode && uiState.totalCount > 0) {
                EditorProgressBar(uiState.copiedCount, uiState.totalCount, uiState.progress)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.isCopyMode -> CopyModeList(
                    uiState = uiState,
                    onCopy = copy,
                    onToggleChecked = { snippet ->
                        if (snippet.isCopied) viewModel.onSnippetUnchecked(snippet) else viewModel.onSnippetCopied(snippet)
                    },
                    onReorder = viewModel::onReorder,
                    onEdit = viewModel::onSnippetLongPress,
                    onPaste = viewModel::onSnippetPasteAfter,
                    onDuplicate = viewModel::onSnippetDuplicate,
                    onDelete = viewModel::onSnippetDelete,
                )
                else -> EditModeField(
                    rawText = uiState.rawText,
                    editRequestedOffset = uiState.editRequestedOffset,
                    onTextChanged = viewModel::onTextChanged,
                    onEditRequestApplied = viewModel::consumeEditRequest,
                )
            }
        }
    }
}

@Composable
private fun CopyModeList(
    uiState: EditorUiState,
    onCopy: (Snippet) -> Unit,
    onToggleChecked: (Snippet) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onEdit: (Snippet) -> Unit,
    onPaste: (Snippet) -> Unit,
    onDuplicate: (Snippet) -> Unit,
    onDelete: (Snippet) -> Unit,
) {
    if (uiState.snippets.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                AppIcons.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(56.dp),
            )
            Text(Strings.EDITOR_EMPTY_COPY_MODE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(Strings.EDITOR_EMPTY_COPY_MODE_HINT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> onReorder(from.index, to.index) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = uiState.snippets.size, key = { index -> uiState.snippets[index].key.encode() }) { index ->
                val snippet = uiState.snippets[index]
                val key = snippet.key.encode()
                var menuOpen by remember { mutableStateOf(false) }
                ReorderableItem(reorderState, key = key) { isDragging ->
                    Box {
                        SnippetRow(
                            snippet = snippet,
                            isDragging = isDragging,
                            onCopy = { onCopy(snippet) },
                            onToggleChecked = { onToggleChecked(snippet) },
                            onContextMenu = { menuOpen = true },
                            dragHandle = { SnippetDragHandle(modifier = Modifier.draggableHandle()) },
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(Strings.SNIPPET_EDIT) }, onClick = { menuOpen = false; onEdit(snippet) })
                            DropdownMenuItem(text = { Text(Strings.SNIPPET_COPY) }, onClick = { menuOpen = false; onCopy(snippet) })
                            DropdownMenuItem(
                                text = { Text(Strings.SNIPPET_PASTE) },
                                onClick = { menuOpen = false; onPaste(snippet) },
                                enabled = uiState.canPaste,
                            )
                            DropdownMenuItem(text = { Text(Strings.SNIPPET_DUPLICATE) }, onClick = { menuOpen = false; onDuplicate(snippet) })
                            DropdownMenuItem(text = { Text(Strings.SNIPPET_DELETE) }, onClick = { menuOpen = false; onDelete(snippet) })
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
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

    // Selection is tracked locally so the cursor does not reset when the text
    // is re-synced from the view model on every keystroke.
    var lastSelection by remember { mutableStateOf<TextRange?>(null) }
    val value = TextFieldValue(
        text = rawText,
        selection = editRequestedOffset?.let { TextRange(it.coerceIn(0, rawText.length)) }
            ?: lastSelection?.takeIf { it.max <= rawText.length }
            ?: TextRange(rawText.length),
    )

    // On desktop there is no on-screen keyboard to manage, so the field takes
    // focus whenever edit mode opens; a row's Edit action also places the caret.
    LaunchedEffect(editRequestedOffset) {
        if (editRequestedOffset != null) {
            lastSelection = TextRange(editRequestedOffset.coerceIn(0, rawText.length))
            onEditRequestApplied()
        }
        focusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    lastSelection = newValue.selection
                    onTextChanged(newValue.text)
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (rawText.isEmpty()) {
                        Text(
                            Strings.EDITOR_TEXT_HINT,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focusRequester),
            )
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun ModeToggle(isCopyMode: Boolean, onToggle: () -> Unit) {
    val modeColors = LocalModeColors.current
    val containerColor by animateColorAsState(if (isCopyMode) modeColors.copyMode else modeColors.editMode, label = "modeToggle")
    val contentColor by animateColorAsState(if (isCopyMode) modeColors.onCopyMode else modeColors.onEditMode, label = "modeToggleContent")

    Tooltip(if (isCopyMode) Strings.EDITOR_SWITCH_TO_EDIT else Strings.EDITOR_SWITCH_TO_COPY) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isCopyMode) AppIcons.TouchApp else AppIcons.EditNote,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isCopyMode) Strings.EDITOR_MODE_COPY else Strings.EDITOR_MODE_EDIT,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun EditorProgressBar(copied: Int, total: Int, progress: Float) {
    val animatedProgress by animateFloatAsState(progress, label = "copyProgress")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (copied == total) Strings.EDITOR_ALL_COPIED else Strings.editorProgress(copied, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Strings.EDITOR_ROW_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}
