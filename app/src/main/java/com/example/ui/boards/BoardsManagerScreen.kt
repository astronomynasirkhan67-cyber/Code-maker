package com.example.ui.boards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BoardEntity
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealDark
import com.example.ui.theme.ArduinoTealLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardsManagerScreen(
    boards: List<BoardEntity>,
    selectedBoard: BoardEntity?,
    onSelectBoard: (BoardEntity) -> Unit,
    onToggleInstall: (BoardEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showOnlyInstalled by remember { mutableStateOf(false) }

    val categories = remember(boards) {
        listOf("All") + boards.map { it.category }.distinct()
    }

    val filteredBoards = remember(boards, searchQuery, selectedCategory, showOnlyInstalled) {
        boards.filter { board ->
            val matchesQuery = board.name.contains(searchQuery, ignoreCase = true) ||
                    board.mcu.contains(searchQuery, ignoreCase = true) ||
                    board.arch.contains(searchQuery, ignoreCase = true) ||
                    board.fqbn.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == "All" || board.category == selectedCategory
            val matchesInstalled = !showOnlyInstalled || board.isInstalled

            matchesQuery && matchesCategory && matchesInstalled
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DeveloperBoard,
                contentDescription = null,
                tint = ArduinoTealLight,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Boards Manager",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Select, install, and configure microcontroller targets",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search boards by name, MCU, or core...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("board_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ArduinoTealLight
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = showOnlyInstalled,
                onClick = { showOnlyInstalled = !showOnlyInstalled },
                label = { Text("Installed Only") },
                leadingIcon = if (showOnlyInstalled) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArduinoTealLight.copy(alpha = 0.2f),
                    selectedLabelColor = ArduinoTealLight
                )
            )

            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ArduinoTealLight.copy(alpha = 0.2f),
                        selectedLabelColor = ArduinoTealLight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Boards List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredBoards, key = { it.id }) { board ->
                val isSelected = selectedBoard?.id == board.id

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("board_card_${board.id}"),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isSelected) ArduinoTealDark.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = board.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ArduinoTealLight
                                        ) {
                                            Text(
                                                text = "ACTIVE TARGET",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = board.fqbn,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = ArduinoAccentOrange
                                    )
                                )
                            }

                            // Installed badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (board.isInstalled) ArduinoAccentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (board.isInstalled) "INSTALLED" else "AVAILABLE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (board.isInstalled) ArduinoAccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = board.description,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Specs Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SpecBadge(icon = Icons.Default.Memory, label = "MCU", value = board.mcu)
                            SpecBadge(icon = Icons.Default.Speed, label = "Clock", value = board.clockSpeed)
                            SpecBadge(icon = Icons.Default.Storage, label = "Flash/RAM", value = "${board.flashSize} / ${board.ramSize}")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Install / Uninstall button
                            OutlinedButton(
                                onClick = { onToggleInstall(board) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(if (board.isInstalled) "Uninstall Core" else "Install Core")
                            }

                            // Select as active target button
                            Button(
                                onClick = { onSelectBoard(board) },
                                enabled = board.isInstalled && !isSelected,
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                            ) {
                                Text(if (isSelected) "Selected Target" else "Set as Target")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ArduinoTealLight,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            )
        }
    }
}
