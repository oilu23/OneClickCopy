package com.oneclickcopy.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.oneclickcopy.backup.AutoBackupManager
import com.oneclickcopy.backup.DriveBackupManager
import com.oneclickcopy.data.Document
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<Document>,
    onDocumentClick: (Document) -> Unit,
    onCreateNew: () -> Unit,
    onDeleteDocument: (Document) -> Unit,
    onRestoreDocuments: (List<Document>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { DriveBackupManager(context) }
    val autoBackupManager = remember { 
        AutoBackupManager(
            context = context,
            driveBackupManager = backupManager,
            onBackupFailed = { errorMessage ->
                Toast.makeText(context, "Auto-backup failed: $errorMessage", Toast.LENGTH_LONG).show()
            },
            onRestoreFailed = { errorMessage ->
                Toast.makeText(context, "Auto-restore failed: $errorMessage", Toast.LENGTH_LONG).show()
            },
            onRestoreSuccess = { restoredDocs ->
                // Merge restored docs with existing
                onRestoreDocuments(restoredDocs)
            }
        )
    }
    
    var showMenu by remember { mutableStateOf(false) }
    var isSignedIn by remember { mutableStateOf(backupManager.isSignedIn()) }
    var isLoading by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf(backupManager.getSignedInAccount()?.email) }
    
    // Auto-backup once when HomeScreen is displayed (app launch or returning from editor)
    var hasBackedUp by remember { mutableStateOf(false) }
    LaunchedEffect(documents) {
        if (!hasBackedUp && documents.isNotEmpty()) {
            hasBackedUp = true
            autoBackupManager.requestBackup(documents)
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            autoBackupManager.cleanup()
        }
    }
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                isSignedIn = true
                userEmail = account?.email
                Toast.makeText(context, "Signed in as ${account?.email}", Toast.LENGTH_SHORT).show()
                
                // Try auto-restore on first login
                autoBackupManager.tryAutoRestore()
            } catch (e: ApiException) {
                Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (isSignedIn) {
                        // Show user avatar/initial
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                                .clickable { showMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userEmail?.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (!isSignedIn) {
                    DropdownMenuItem(
                        text = { Text("Sign in with Google") },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                        onClick = {
                            showMenu = false
                            signInLauncher.launch(backupManager.getSignInIntent())
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Backup to Drive") },
                        leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                        onClick = {
                            showMenu = false
                            isLoading = true
                            scope.launch {
                                val result = backupManager.backup(documents)
                                isLoading = false
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { e ->
                                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Sign out") },
                        leadingIcon = { Icon(Icons.Default.Logout, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                backupManager.signOut()
                                isSignedIn = false
                                userEmail = null
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = Color(0xFF006D6D),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create new document")
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // "Last opened" header
                Text(
                    text = "Last opened",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                if (documents.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No documents yet",
                                color = Color.Gray,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to create one",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        items(documents, key = { it.id }) { document ->
                            DocumentListItem(
                                document = document,
                                onClick = { onDocumentClick(document) },
                                onDelete = { onDeleteDocument(document) }
                            )
                        }
                    }
                }
            }
            
            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DocumentListItem(
    document: Document,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
    val dateString = remember(document.updatedAt) {
        val now = System.currentTimeMillis()
        val diff = now - document.updatedAt
        val oneDay = 24 * 60 * 60 * 1000
        
        if (diff < oneDay) {
            timeFormat.format(Date(document.updatedAt))
        } else {
            dateFormat.format(Date(document.updatedAt))
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Document icon
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = Color(0xFF8AB4F8),
            modifier = Modifier.size(40.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Title and date
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title.ifEmpty { "Untitled" },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Opened by me $dateString",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        
        // More options
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.Gray
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
