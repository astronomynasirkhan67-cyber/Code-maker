package com.example.ui.projects

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.ProjectEntity
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    currentProjectId: Long?,
    availableBoards: List<BoardEntity>,
    onSelectProject: (Long) -> Unit,
    onCreateProject: (name: String, description: String, boardId: String) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onRenameProject: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDesc by remember { mutableStateOf("") }
    var selectedBoardId by remember { mutableStateOf("arduino_uno") }

    var projectForMenu by remember { mutableStateOf<ProjectEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }

    val filteredProjects = remember(projects, searchQuery) {
        projects.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = ArduinoTealLight,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sketches & Projects",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Manage your local Arduino programs stored on device",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search your sketches...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArduinoTealLight)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Projects List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredProjects, key = { it.id }) { project ->
                    val isCurrent = project.id == currentProjectId
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(project.updatedAt))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProject(project.id) }
                            .testTag("project_item_${project.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) ArduinoTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrent) ArduinoTeal else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = if (isCurrent) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ArduinoTealLight
                                            ) {
                                                Text(
                                                    text = "CURRENT",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = androidx.compose.ui.graphics.Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (project.description.isNotEmpty()) {
                                        Text(
                                            text = project.description,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            maxLines = 1
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Target: ${project.selectedBoardId}  •  Updated: $formattedDate",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            // Overflow actions for project
                            IconButton(onClick = { projectForMenu = project }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Project Options")
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to Create Sketch
        FloatingActionButton(
            onClick = {
                newProjectName = ""
                newProjectDesc = ""
                showCreateDialog = true
            },
            containerColor = ArduinoTeal,
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("create_sketch_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Sketch")
        }

        // Project Context Menu
        DropdownMenu(
            expanded = projectForMenu != null,
            onDismissRequest = { projectForMenu = null }
        ) {
            DropdownMenuItem(
                text = { Text("Open in Editor") },
                onClick = {
                    projectForMenu?.let { onSelectProject(it.id) }
                    projectForMenu = null
                },
                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Rename Sketch") },
                onClick = {
                    renameName = projectForMenu?.name ?: ""
                    showRenameDialog = true
                    projectForMenu = null
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Share / Export Sketch") },
                onClick = {
                    val p = projectForMenu
                    if (p != null) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "${p.name}.ino")
                            putExtra(Intent.EXTRA_TEXT, "// Mobile Arduino IDE Sketch: ${p.name}\n// Board: ${p.selectedBoardId}\n")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Sketch"))
                    }
                    projectForMenu = null
                },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete Sketch", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    projectForMenu?.let { onDeleteProject(it.id) }
                    projectForMenu = null
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }

        // Create Sketch Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New Sketch") },
                text = {
                    Column {
                        Text("Sketch Name:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            placeholder = { Text("e.g. ServoController") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Description (optional):", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newProjectDesc,
                            onValueChange = { newProjectDesc = it },
                            placeholder = { Text("Controls 2 servos via PWM...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                onCreateProject(newProjectName, newProjectDesc, selectedBoardId)
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                    ) {
                        Text("Create Sketch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Rename Dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Sketch") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = renameName,
                            onValueChange = { renameName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val activePId = currentProjectId
                            if (activePId != null && renameName.isNotBlank()) {
                                onRenameProject(activePId, renameName)
                                showRenameDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
