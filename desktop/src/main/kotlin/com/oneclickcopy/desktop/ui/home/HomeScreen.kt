package com.oneclickcopy.desktop.ui.home

import com.oneclickcopy.ui.icons.AppIcons
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import com.oneclickcopy.desktop.DocumentTransfer
import com.oneclickcopy.desktop.ui.KeyShortcutHandler
import com.oneclickcopy.desktop.ui.LocalAppWindow
import com.oneclickcopy.desktop.ui.Strings
import com.oneclickcopy.desktop.ui.Tooltip
import com.oneclickcopy.desktop.ui.theme.Grey30
import com.oneclickcopy.desktop.ui.theme.Neutral90
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.io.File
import java.nio.file.Path

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    alwaysOnTop: Boolean,
    onToggleAlwaysOnTop: () -> Unit,
    onOpenDocument: (Long, isNew: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val signInState by viewModel.signInState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val window = LocalAppWindow.current

    var isSearchActive by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    val createDocument = { viewModel.onCreateDocument { id -> onOpenDocument(id, true) } }

    KeyShortcutHandler { key ->
        when {
            key.isCtrlPressed && key.key == Key.N -> { createDocument(); true }
            key.isCtrlPressed && key.key == Key.F -> {
                if (isSearchActive) searchFocusRequester.requestFocus() else isSearchActive = true
                true
            }
            key.key == Key.Escape && isSearchActive -> {
                viewModel.onSearchQueryChanged("")
                isSearchActive = false
                true
            }
            else -> false
        }
    }

    // Snackbars run in their own scope: consuming the event clears it, which
    // would cancel a snackbar shown from inside the LaunchedEffect itself.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        viewModel.consumeEvent()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarScope.launch {
            when (current) {
                is HomeEvent.DocumentDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = Strings.HOME_DELETED_MESSAGE,
                        actionLabel = Strings.HOME_UNDO,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(current.document)
                }
                is HomeEvent.Message -> snackbarHostState.showSnackbar(current.text)
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text(Strings.HOME_SEARCH_HINT) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        )
                    } else {
                        Text(Strings.HOME_TITLE)
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        SyncStatusChip(state = uiState.syncState, modifier = Modifier.padding(end = 4.dp))
                    }
                    Tooltip(if (isSearchActive) Strings.HOME_SEARCH_CLOSE else Strings.HOME_SEARCH_OPEN) {
                        IconButton(onClick = {
                            if (isSearchActive) viewModel.onSearchQueryChanged("")
                            isSearchActive = !isSearchActive
                        }) {
                            Icon(
                                if (isSearchActive) AppIcons.Close else AppIcons.Search,
                                contentDescription = if (isSearchActive) Strings.HOME_SEARCH_CLOSE else Strings.HOME_SEARCH_OPEN,
                            )
                        }
                    }
                    PinButton(alwaysOnTop, onToggleAlwaysOnTop)
                    Box {
                        Tooltip(Strings.SYNC_ACCOUNT_MENU) {
                            IconButton(onClick = { accountMenuExpanded = true }) {
                                Icon(AppIcons.AccountCircle, contentDescription = Strings.SYNC_ACCOUNT_MENU)
                            }
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            if (!uiState.isSignedIn) {
                                DropdownMenuItem(
                                    text = { Text(Strings.BACKUP_SIGN_IN) },
                                    leadingIcon = { Icon(AppIcons.AccountCircle, null) },
                                    onClick = {
                                        accountMenuExpanded = false
                                        viewModel.onSignInClicked()
                                    },
                                )
                            } else {
                                uiState.accountLabel?.let { account ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(Strings.syncSignedInAs(account), style = MaterialTheme.typography.bodySmall)
                                        },
                                        onClick = {},
                                        enabled = false,
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text(Strings.SYNC_NOW) },
                                    leadingIcon = { Icon(AppIcons.CloudUpload, null) },
                                    onClick = {
                                        accountMenuExpanded = false
                                        viewModel.onSyncNow()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(Strings.BACKUP_SIGN_OUT) },
                                    leadingIcon = { Icon(AppIcons.Logout, null) },
                                    onClick = {
                                        accountMenuExpanded = false
                                        viewModel.onSignOut()
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(Strings.TRANSFER_EXPORT) },
                                leadingIcon = { Icon(AppIcons.FileDownload, null) },
                                onClick = {
                                    accountMenuExpanded = false
                                    chooseFile(window, Strings.TRANSFER_EXPORT, FileDialog.SAVE)
                                        ?.let(viewModel::onExportTo)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings.TRANSFER_IMPORT) },
                                leadingIcon = { Icon(AppIcons.FileUpload, null) },
                                onClick = {
                                    accountMenuExpanded = false
                                    chooseFile(window, Strings.TRANSFER_IMPORT, FileDialog.LOAD)
                                        ?.let(viewModel::onImportFrom)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            Tooltip(Strings.HOME_CREATE_DOCUMENT) {
                FloatingActionButton(
                    onClick = createDocument,
                    containerColor = Grey30,
                    contentColor = Neutral90,
                    shape = CircleShape,
                ) {
                    Icon(AppIcons.Add, contentDescription = Strings.HOME_CREATE_DOCUMENT)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.documents.isEmpty() -> EmptyState(
                    isSearching = uiState.isSearching,
                    query = uiState.searchQuery,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.documents, key = { it.id }) { document ->
                            DocumentRow(
                                document = document,
                                onClick = { onOpenDocument(document.id, false) },
                                onDuplicate = { copyTitle -> viewModel.onDuplicateDocument(document, copyTitle) },
                                onDelete = { pendingDeleteId = document.id },
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }

    val documentPendingDelete = pendingDeleteId?.let { id -> uiState.documents.firstOrNull { it.id == id } }
    LaunchedEffect(pendingDeleteId, documentPendingDelete) {
        if (pendingDeleteId != null && documentPendingDelete == null) pendingDeleteId = null
    }

    GitHubSignInDialog(state = signInState, onCancel = viewModel::onSignInCancelled)

    if (documentPendingDelete != null) {
        DeleteDocumentDialog(
            document = documentPendingDelete,
            onConfirm = {
                viewModel.onDeleteDocument(documentPendingDelete)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
fun PinButton(alwaysOnTop: Boolean, onToggle: () -> Unit) {
    val label = if (alwaysOnTop) Strings.UNPIN_ON_TOP else Strings.PIN_ON_TOP
    Tooltip(label) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (alwaysOnTop) AppIcons.PushPin else AppIcons.PushPinOutline,
                contentDescription = label,
                tint = if (alwaysOnTop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chooseFile(window: java.awt.Frame?, title: String, mode: Int): Path? {
    val dialog = FileDialog(window, title, mode).apply {
        if (mode == FileDialog.SAVE) file = DocumentTransfer.defaultFileName() else file = "*.json"
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name).toPath()
}

@Composable
private fun EmptyState(isSearching: Boolean, query: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (isSearching) AppIcons.Search else AppIcons.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = if (isSearching) Strings.HOME_NO_RESULTS_TITLE else Strings.HOME_EMPTY_TITLE,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (isSearching) Strings.homeNoResultsSubtitle(query) else Strings.HOME_EMPTY_SUBTITLE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
