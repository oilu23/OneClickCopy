package com.oneclickcopy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.Document
import com.oneclickcopy.ui.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.*

data class TextItem(
    val id: Int,
    val text: String,
    val isCopied: Boolean = false
)

@Composable
fun OneClickCopyApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val documents by database.documentDao().getAllDocuments().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            // Debounce navigation with auto-reset after 500ms
            var lastNavTime by remember { mutableStateOf(0L) }
            
            fun canNavigate(): Boolean {
                val now = System.currentTimeMillis()
                return if (now - lastNavTime > 500) {
                    lastNavTime = now
                    true
                } else {
                    false
                }
            }
            
            HomeScreen(
                documents = documents,
                onDocumentClick = { doc ->
                    if (canNavigate()) {
                        navController.navigate("editor/${doc.id}") {
                            launchSingleTop = true
                        }
                    }
                },
                onCreateNew = {
                    if (canNavigate()) {
                        scope.launch {
                            val newId = database.documentDao().insertDocument(
                                Document(title = "Untitled", content = "")
                            )
                            navController.navigate("editor/$newId") {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onDeleteDocument = { doc ->
                    scope.launch {
                        database.documentDao().deleteDocument(doc)
                    }
                },
                onRestoreDocuments = { restoredDocs ->
                    scope.launch {
                        // Insert all restored documents
                        restoredDocs.forEach { doc ->
                            // Create new document with same content but new ID
                            database.documentDao().insertDocument(
                                Document(
                                    title = doc.title,
                                    content = doc.content,
                                    copiedItems = doc.copiedItems,
                                    createdAt = doc.createdAt,
                                    updatedAt = doc.updatedAt
                                )
                            )
                        }
                    }
                }
            )
        }
        
        composable(
            route = "editor/{documentId}",
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            var hasNavigatedBack by remember { mutableStateOf(false) }
            
            EditorScreen(
                documentId = documentId,
                database = database,
                onNavigateBack = {
                    if (!hasNavigatedBack) {
                        hasNavigatedBack = true
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    documentId: Long,
    database: AppDatabase,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var document by remember { mutableStateOf<Document?>(null) }
    var title by remember { mutableStateOf("") }
    var isEditingTitle by remember { mutableStateOf(false) }
    var isCopyMode by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<TextItem>()) }
    // Track copied items by their text - persists across mode switches AND navigation
    var copiedTexts by remember { mutableStateOf(setOf<String>()) }
    // Track if items were reordered in copy mode
    var wasReordered by remember { mutableStateOf(false) }
    
    // Helper to parse/serialize copiedItems
    fun parseCopiedItems(json: String): Set<String> {
        if (json.isBlank()) return emptySet()
        return try {
            json.removeSurrounding("[", "]")
                .split("\",\"")
                .map { it.trim('"') }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) { emptySet() }
    }
    
    fun serializeCopiedItems(items: Set<String>): String {
        if (items.isEmpty()) return ""
        return items.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
            .let { "[$it]" }
    }
    
    // Load document
    LaunchedEffect(documentId) {
        document = database.documentDao().getDocumentById(documentId)
        document?.let {
            title = it.title
            rawText = it.content
            copiedTexts = parseCopiedItems(it.copiedItems)
        }
    }
    
    // Auto-save with debounce (waits 500ms after typing stops)
    LaunchedEffect(rawText, title, copiedTexts) {
        if (document != null) {
            delay(500) // Debounce - wait for user to stop typing
            database.documentDao().updateDocument(
                document!!.copy(
                    title = title,
                    content = rawText,
                    copiedItems = serializeCopiedItems(copiedTexts),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    // Convert raw text to items when switching to copy mode
    LaunchedEffect(isCopyMode) {
        if (isCopyMode) {
            wasReordered = false
            items = rawText.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, line -> 
                    val trimmed = line.trim()
                    TextItem(index, trimmed, isCopied = trimmed in copiedTexts)
                }
        } else {
            // Only update rawText if items were reordered, to preserve blank lines
            if (wasReordered && items.isNotEmpty()) {
                rawText = items.joinToString("\n") { it.text }
            }
        }
    }
    
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            wasReordered = true
            items = items.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    )
    
    // Detect if keyboard is visible
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingTitle) {
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = title.ifEmpty { "Untitled" },
                            modifier = Modifier.clickable { isEditingTitle = true }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditingTitle) {
                        IconButton(onClick = { isEditingTitle = false }) {
                            Icon(Icons.Default.Check, contentDescription = "Done editing title")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Hide bottom bar when keyboard is visible
            if (!imeVisible) {
                BottomBar(
                    isCopyMode = isCopyMode,
                    onToggle = { isCopyMode = it },
                    onReset = {
                        copiedTexts = emptySet()
                        items = items.map { it.copy(isCopied = false) }
                    }
                )
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isCopyMode) {
                // Copy Mode - List view with checkboxes
                LazyColumn(
                    state = reorderState.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderState)
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        ReorderableItem(reorderState, key = item.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            
                            CopyListItem(
                                item = item,
                                isDragging = isDragging,
                                elevation = elevation,
                                onCopy = {
                                    // Copy to clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied text", item.text)
                                    clipboard.setPrimaryClip(clip)
                                    
                                    // Mark as copied (both in items and persistent set)
                                    copiedTexts = copiedTexts + item.text
                                    items = items.map { 
                                        if (it.id == item.id) it.copy(isCopied = true) else it 
                                    }
                                },
                                modifier = Modifier.detectReorderAfterLongPress(reorderState)
                            )
                        }
                    }
                }
            } else {
                // Edit Mode - Text editor (scrollable)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    BasicTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 400.dp)
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            if (rawText.isEmpty()) {
                                Text(
                                    text = "Enter your text here...\nEach line becomes a copy item.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 18.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    // Spacer that grows when keyboard is visible
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                    )
                }
            }
        }
    }
}

@Composable
fun CopyListItem(
    item: TextItem,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        if (isDragging) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.background
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable { onCopy() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(end = 8.dp)
        )
        
        // Text content
        Text(
            text = item.text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        // Checkbox indicator
        Icon(
            imageVector = if (item.isCopied) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (item.isCopied) "Copied" else "Not copied",
            tint = if (item.isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun BottomBar(
    isCopyMode: Boolean,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    val toggleColor by animateColorAsState(
        if (isCopyMode) Color(0xFF4CAF50) else Color(0xFFB71C1C)
    )
    
    Surface(
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 76.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left spacer - takes up space equal to reset button area
                Box(modifier = Modifier.width(48.dp))
                
                // Center section with toggle
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = toggleColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onToggle(!isCopyMode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isCopyMode) {
                                // OFF state - circle on left
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.9f))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OFF",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                // ON state - text on left
                                Text(
                                    text = "ON",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.9f))
                                )
                            }
                        }
                    }
                }
                
                // Right section - reset button or spacer
                Box(modifier = Modifier.width(48.dp)) {
                    if (isCopyMode) {
                        IconButton(onClick = onReset) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset checkmarks",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
