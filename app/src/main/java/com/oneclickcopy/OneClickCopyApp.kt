package com.oneclickcopy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.burnoutcrew.reorderable.*

data class TextItem(
    val id: Int,
    val text: String,
    val isCopied: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickCopyApp() {
    var title by remember { mutableStateOf("Title") }
    var isEditingTitle by remember { mutableStateOf(false) }
    var isCopyMode by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<TextItem>()) }
    
    val context = LocalContext.current
    
    // Convert raw text to items when switching to copy mode
    LaunchedEffect(isCopyMode) {
        if (isCopyMode) {
            items = rawText.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, line -> TextItem(index, line.trim()) }
        } else {
            // Convert items back to raw text when switching to edit mode
            rawText = items.joinToString("\n") { it.text }
        }
    }
    
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            items = items.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    )
    
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
                            text = title,
                            modifier = Modifier.clickable { isEditingTitle = true }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { /* Navigate back */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditingTitle) {
                        IconButton(onClick = { isEditingTitle = false }) {
                            Icon(Icons.Default.Check, contentDescription = "Done editing title")
                        }
                    } else {
                        IconButton(onClick = { /* Pin action */ }) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            BottomBar(
                isCopyMode = isCopyMode,
                onToggle = { isCopyMode = it },
                onReset = {
                    items = items.map { it.copy(isCopied = false) }
                }
            )
        }
    ) { padding ->
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
                                    
                                    // Mark as copied
                                    items = items.map { 
                                        if (it.id == item.id) it.copy(isCopied = true) else it 
                                    }
                                    
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.detectReorderAfterLongPress(reorderState)
                            )
                        }
                    }
                }
            } else {
                // Edit Mode - Text editor
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
                        .fillMaxSize()
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
        color = Color(0xFFE0E0E0),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Purple accent line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0xFF9C27B0))
            )
            
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
                                tint = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}
