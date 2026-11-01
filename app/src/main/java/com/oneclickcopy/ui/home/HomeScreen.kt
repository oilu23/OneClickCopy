package com.oneclickcopy.ui.home

import com.oneclickcopy.ui.icons.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import com.oneclickcopy.data.DocumentTransfer
import com.oneclickcopy.ui.common.resolve
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneclickcopy.R
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.ui.theme.Grey30
import com.oneclickcopy.ui.theme.Neutral90

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDocumentClick: (Long) -> Unit,
    onCreateDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchActive by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    // Held by id rather than by entity so the pending deletion survives a
    // configuration change and always reflects current content.
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    val signInState by viewModel.signInState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(DocumentTransfer.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::onExportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onImportFrom) }

    val deletedMessage = stringResource(R.string.home_deleted_message)
    val undoLabel = stringResource(R.string.home_undo)

    // UiText must be resolved inside composition, not in the LaunchedEffect body.
    val messageText = when (val current = event) {
        is HomeEvent.Message -> current.text.resolve()
        is HomeEvent.Error -> current.message.resolve()
        else -> null
    }

    LaunchedEffect(event) {
        when (val current = event) {
            is HomeEvent.DocumentDeleted -> {
                val result = snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onUndoDelete(current.document)
                }
                viewModel.consumeEvent()
            }
            is HomeEvent.Message -> {
                snackbarHostState.showSnackbar(messageText.orEmpty())
                viewModel.consumeEvent()
            }
            is HomeEvent.Error -> {
                snackbarHostState.showSnackbar(messageText.orEmpty())
                viewModel.consumeEvent()
            }
            null -> Unit
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
                            placeholder = { Text(stringResource(R.string.home_search_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    } else {
                        Text(stringResource(R.string.home_title))
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        SyncStatusChip(
                            state = uiState.syncState,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isSearchActive) viewModel.onSearchQueryChanged("")
                            isSearchActive = !isSearchActive
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) AppIcons.Close else AppIcons.Search,
                            contentDescription = stringResource(
                                if (isSearchActive) R.string.home_search_close
                                else R.string.home_search_open
                            ),
                        )
                    }
                    Box {
                        IconButton(onClick = { accountMenuExpanded = true }) {
                            Icon(
                                AppIcons.AccountCircle,
                                contentDescription = stringResource(R.string.sync_account_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            if (!uiState.isSignedIn) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.backup_sign_in)) },
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
                                            Text(
                                                stringResource(R.string.sync_signed_in_as, account),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                        onClick = { },
                                        enabled = false,
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sync_now)) },
                                    leadingIcon = { Icon(AppIcons.CloudUpload, null) },
                                    onClick = {
                                        accountMenuExpanded = false
                                        viewModel.onSyncNow()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.backup_sign_out)) },
                                    leadingIcon = { Icon(AppIcons.Logout, null) },
                                    onClick = {
                                        accountMenuExpanded = false
                                        viewModel.onSignOut()
                                    },
                                )
                            }

                            HorizontalDivider()

                            // Always offered, signed in or not: a file copy works
                            // with no account and no network.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.transfer_export)) },
                                leadingIcon = { Icon(AppIcons.FileDownload, null) },
                                onClick = {
                                    accountMenuExpanded = false
                                    exportLauncher.launch(DocumentTransfer.defaultFileName())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.transfer_import)) },
                                leadingIcon = { Icon(AppIcons.FileUpload, null) },
                                onClick = {
                                    accountMenuExpanded = false
                                    importLauncher.launch(arrayOf(DocumentTransfer.MIME_TYPE))
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
            FloatingActionButton(
                onClick = { viewModel.onCreateDocument(onCreateDocument) },
                containerColor = Grey30,
                contentColor = Neutral90,
                shape = CircleShape,
            ) {
                Icon(
                    AppIcons.Add,
                    contentDescription = stringResource(R.string.home_create_document),
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
                uiState.documents.isEmpty() -> {
                    EmptyState(
                        isSearching = uiState.isSearching,
                        query = uiState.searchQuery,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        // Bottom padding clears both the FAB and the system
                        // navigation bar so the last row stays reachable.
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.documents, key = { it.id }) { document ->
                            DocumentRow(
                                document = document,
                                onClick = { onDocumentClick(document.id) },
                                onDuplicate = { copyTitle ->
                                    viewModel.onDuplicateDocument(document, copyTitle)
                                },
                                onDelete = { pendingDeleteId = document.id },
                            )
                        }
                    }
                }
            }
        }
    }

    // Resolved from current state so the dialog cannot show a stale title.
    val documentPendingDelete = pendingDeleteId?.let { id ->
        uiState.documents.firstOrNull { it.id == id }
    }

    // Dismiss by itself if the document disappears while the dialog is open,
    // for example because a sync removed it. Done in an effect rather than
    // during composition, which must stay free of side effects.
    LaunchedEffect(pendingDeleteId, documentPendingDelete) {
        if (pendingDeleteId != null && documentPendingDelete == null) {
            pendingDeleteId = null
        }
    }

    GitHubSignInDialog(
        state = signInState,
        onCancel = viewModel::onSignInCancelled,
    )

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
private fun EmptyState(
    isSearching: Boolean,
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (isSearching) AppIcons.Search else AppIcons.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = stringResource(
                if (isSearching) R.string.home_no_results_title else R.string.home_empty_title
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (isSearching) {
                stringResource(R.string.home_no_results_subtitle, query)
            } else {
                stringResource(R.string.home_empty_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
