package com.example.ui.boards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.boards.CoreInstallProgress
import com.example.boards.PlatformPackage
import com.example.data.local.entity.BoardEntity
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoAccentRed
import com.example.ui.theme.ArduinoAccentYellow
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealDark
import com.example.ui.theme.ArduinoTealLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardsManagerScreen(
    boards: List<BoardEntity>,
    selectedBoard: BoardEntity?,
    esp32Platform: PlatformPackage,
    packageUrls: List<String>,
    installProgress: CoreInstallProgress?,
    onSelectBoard: (BoardEntity) -> Unit,
    onToggleInstall: (BoardEntity) -> Unit,
    onInstallEsp32Core: (version: String) -> Unit,
    onUninstallEsp32Core: () -> Unit,
    onUpdateIndexes: () -> Unit,
    onAddPackageUrl: (String) -> Unit,
    onRemovePackageUrl: (String) -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showOnlyInstalled by remember { mutableStateOf(false) }
    var showAddUrlDialog by remember { mutableStateOf(false) }

    val categories = remember(boards) {
        listOf("All", "ESP32", "ESP32-S2", "ESP32-S3", "ESP32-C3", "ESP32-C6", "AVR")
    }

    val filteredBoards = remember(boards, searchQuery, selectedCategory, showOnlyInstalled) {
        boards.filter { board ->
            val matchesQuery = board.name.contains(searchQuery, ignoreCase = true) ||
                    board.mcu.contains(searchQuery, ignoreCase = true) ||
                    board.arch.contains(searchQuery, ignoreCase = true) ||
                    board.fqbn.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "ESP32" -> board.name.contains("ESP32", ignoreCase = true) &&
                        !board.name.contains("ESP32-S", ignoreCase = true) &&
                        !board.name.contains("ESP32-C", ignoreCase = true)
                "ESP32-S2" -> board.name.contains("ESP32-S2", ignoreCase = true) || board.mcu.contains("ESP32-S2", ignoreCase = true)
                "ESP32-S3" -> board.name.contains("ESP32-S3", ignoreCase = true) || board.mcu.contains("ESP32-S3", ignoreCase = true)
                "ESP32-C3" -> board.name.contains("ESP32-C3", ignoreCase = true) || board.mcu.contains("ESP32-C3", ignoreCase = true)
                "ESP32-C6" -> board.name.contains("ESP32-C6", ignoreCase = true) || board.mcu.contains("ESP32-C6", ignoreCase = true)
                "AVR" -> board.arch.equals("avr", ignoreCase = true) || board.category.contains("AVR", ignoreCase = true)
                else -> board.category == selectedCategory
            }

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
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DeveloperBoard,
                    contentDescription = null,
                    tint = ArduinoTealLight,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Boards & Platforms",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Manage Arduino & ESP32 platforms, cores, and target boards",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            IconButton(
                onClick = { showAddUrlDialog = true },
                modifier = Modifier.testTag("package_urls_button")
            ) {
                Icon(Icons.Default.Link, contentDescription = "Package URLs", tint = ArduinoTealLight)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs: [Board Selector] & [Boards Manager (Cores)]
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ArduinoTealLight,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ArduinoTealLight
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Board Selector", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Boards Manager", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            // TAB 0: Board Selector & Catalog
            BoardCatalogTab(
                boards = filteredBoards,
                selectedBoard = selectedBoard,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedCategory = selectedCategory,
                onCategoryChange = { selectedCategory = it },
                categories = categories,
                showOnlyInstalled = showOnlyInstalled,
                onToggleShowInstalled = { showOnlyInstalled = !showOnlyInstalled },
                onSelectBoard = onSelectBoard,
                onToggleInstall = onToggleInstall,
                onOpenBoardsManager = { selectedTab = 1 }
            )
        } else {
            // TAB 1: Boards Manager (Core Platform Installer)
            BoardsManagerCoresTab(
                esp32Platform = esp32Platform,
                packageUrls = packageUrls,
                installProgress = installProgress,
                onInstallEsp32Core = onInstallEsp32Core,
                onUninstallEsp32Core = onUninstallEsp32Core,
                onUpdateIndexes = onUpdateIndexes,
                onManageUrls = { showAddUrlDialog = true }
            )
        }
    }

    // Package URLs Manager Dialog
    if (showAddUrlDialog) {
        PackageUrlsDialog(
            currentUrls = packageUrls,
            onAddUrl = onAddPackageUrl,
            onRemoveUrl = onRemovePackageUrl,
            onDismiss = { showAddUrlDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardCatalogTab(
    boards: List<BoardEntity>,
    selectedBoard: BoardEntity?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    categories: List<String>,
    showOnlyInstalled: Boolean,
    onToggleShowInstalled: () -> Unit,
    onSelectBoard: (BoardEntity) -> Unit,
    onToggleInstall: (BoardEntity) -> Unit,
    onOpenBoardsManager: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("board_search_input"),
            placeholder = { Text("Search boards by name, MCU, FQBN...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = ArduinoTealLight)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ArduinoTealLight,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Categories & Filter Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ArduinoTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }

            FilterChip(
                selected = showOnlyInstalled,
                onClick = onToggleShowInstalled,
                label = { Text("Installed Only", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        if (showOnlyInstalled) Icons.Default.Check else Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArduinoAccentGreen,
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Boards List
        if (boards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No boards match your search",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenBoardsManager,
                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                    ) {
                        Text("Open Boards Manager to Install Platforms")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(boards, key = { it.id }) { board ->
                    BoardCard(
                        board = board,
                        isSelected = selectedBoard?.id == board.id,
                        onSelect = { onSelectBoard(board) },
                        onToggleInstall = { onToggleInstall(board) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    board: BoardEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleInstall: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("board_card_${board.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) ArduinoTeal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) ArduinoTeal else ArduinoTeal.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else ArduinoTealLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = board.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ArduinoAccentGreen,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "FQBN: ${board.fqbn}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArduinoTealLight,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) ArduinoTealLight else ArduinoTeal
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("select_board_${board.id}")
                ) {
                    Text(if (isSelected) "Selected" else "Select", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(board.mcu, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(board.clockSpeed, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(board.flashSize, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Core Info Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Core: ${if (board.fqbn.startsWith("esp32")) "esp32 by Espressif Systems (v3.1.1)" else "arduino:avr (v1.8.6)"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (board.isInstalled) ArduinoAccentGreen.copy(alpha = 0.15f) else ArduinoAccentRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (board.isInstalled) "✓ Core Installed" else "✕ Core Missing",
                        color = if (board.isInstalled) ArduinoAccentGreen else ArduinoAccentRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardsManagerCoresTab(
    esp32Platform: PlatformPackage,
    packageUrls: List<String>,
    installProgress: CoreInstallProgress?,
    onInstallEsp32Core: (version: String) -> Unit,
    onUninstallEsp32Core: () -> Unit,
    onUpdateIndexes: () -> Unit,
    onManageUrls: () -> Unit
) {
    var selectedVersion by remember(esp32Platform) {
        mutableStateOf(esp32Platform.installedVersion ?: esp32Platform.availableVersions.firstOrNull() ?: "3.1.1")
    }
    var showVersionDropdown by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Platform Management Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Platform Package Indexes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onManageUrls,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("URLs (${packageUrls.size})", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onUpdateIndexes,
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("update_indexes_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Update Index", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Official Espressif stable index preconfigured. Tap 'Update Index' to fetch latest release metadata.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        // Live Installation Progress Bar (if active)
        if (installProgress != null && !installProgress.isFinished) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ArduinoTealDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Installing ESP32 Platform...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(installProgress.progress * 100).toInt()}%",
                                color = ArduinoAccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { installProgress.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = ArduinoAccentGreen,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "[${installProgress.stage}] ${installProgress.detail}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ESP32 Platform Card (Main Core requested by user)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = esp32Platform.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "by ${esp32Platform.maintainer} • Category: ${esp32Platform.category}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (esp32Platform.isInstalled) ArduinoAccentGreen.copy(alpha = 0.15f) else ArduinoAccentRed.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (esp32Platform.isInstalled) ArduinoAccentGreen else ArduinoAccentRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (esp32Platform.isInstalled) "Installed: ✓ v${esp32Platform.installedVersion}" else "Not Installed",
                                    color = if (esp32Platform.isInstalled) ArduinoAccentGreen else ArduinoAccentRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = esp32Platform.description,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Version Selection Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Select Platform Version:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Box {
                                OutlinedButton(
                                    onClick = { showVersionDropdown = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("esp32_version_selector")
                                ) {
                                    Text("v$selectedVersion ▼", fontWeight = FontWeight.SemiBold)
                                }

                                DropdownMenu(
                                    expanded = showVersionDropdown,
                                    onDismissRequest = { showVersionDropdown = false }
                                ) {
                                    esp32Platform.availableVersions.forEach { ver ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(ver)
                                                    if (ver == esp32Platform.installedVersion) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("(Installed)", color = ArduinoAccentGreen, fontSize = 11.sp)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedVersion = ver
                                                showVersionDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons: [Install] / [Update] / [Uninstall]
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (esp32Platform.isInstalled) {
                                OutlinedButton(
                                    onClick = onUninstallEsp32Core,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ArduinoAccentRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("uninstall_esp32_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Uninstall", fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = { onInstallEsp32Core(selectedVersion) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (esp32Platform.isInstalled && esp32Platform.installedVersion == selectedVersion) ArduinoTealLight else ArduinoAccentGreen
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("install_esp32_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (esp32Platform.isInstalled && esp32Platform.installedVersion == selectedVersion) "Reinstall" else "Install",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Supported ESP32 Board Families List
                    Text(
                        text = "Exposed Target Board Families (Included in ESP32 Core):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ESP32 Dev Module", "ESP32 WROVER", "ESP32-S2", "ESP32-S3", "ESP32-C3", "ESP32-C6", "NodeMCU-32S").forEach { family ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ArduinoTeal.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = family,
                                    fontSize = 10.sp,
                                    color = ArduinoTealDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Arduino AVR Platform Card (Standard Uno, Nano, Mega)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Arduino AVR Boards",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "by Arduino Official • Built-in Core",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ArduinoAccentGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "✓ Installed: v1.8.6",
                                color = ArduinoAccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Includes Arduino Uno, Nano, Mega 2560, Leonardo, and Pro Mini boards.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageUrlsDialog(
    currentUrls: List<String>,
    onAddUrl: (String) -> Unit,
    onRemoveUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newUrlInput by remember { mutableStateOf("") }

    val defaultUrls = listOf(
        "https://espressif.github.io/arduino-esp32/package_esp32_index.json" to "Espressif ESP32 Stable",
        "https://espressif.github.io/arduino-esp32/package_esp32_dev_index.json" to "Espressif ESP32 Development"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Board Manager Package URLs") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Configure JSON index URLs for Arduino and ESP32 board definitions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newUrlInput,
                    onValueChange = { newUrlInput = it },
                    placeholder = { Text("https://.../package_xxx_index.json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        if (newUrlInput.isNotBlank()) {
                            onAddUrl(newUrlInput.trim())
                            newUrlInput = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = newUrlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                ) {
                    Text("Add URL")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Configured URLs (${currentUrls.size}):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    items(currentUrls) { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = url,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { onRemoveUrl(url) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ArduinoAccentRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Quick Add Presets:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )

                defaultUrls.forEach { (presetUrl, label) ->
                    TextButton(
                        onClick = { onAddUrl(presetUrl) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("+ $label", fontSize = 11.sp, color = ArduinoTealLight)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
            ) {
                Text("Close")
            }
        }
    )
}
