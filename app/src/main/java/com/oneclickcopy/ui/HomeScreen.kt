package com.oneclickcopy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneclickcopy.data.Document
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<Document>,
    onDocumentClick: (Document) -> Unit,
    onCreateNew: () -> Unit,
    onDeleteDocument: (Document) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
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
