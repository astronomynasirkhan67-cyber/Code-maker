package com.example.ui.home

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.ProjectEntity
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealDark
import com.example.ui.theme.ArduinoTealLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    projects: List<ProjectEntity>,
    currentProjectId: Long?,
    availableBoards: List<BoardEntity>,
    onSelectProject: (Long) -> Unit,
    onCreateNewSketch: (name: String, boardId: String) -> Unit,
    onImportSketch: (name: String, code: String, boardId: String) -> Unit,
    onOpenAllProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateWorkflowStep: (step: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewSketchDialog by remember { mutableStateOf(false) }
    var newSketchName by remember { mutableStateOf("") }
    var selectedBoardId by remember { mutableStateOf("arduino_uno") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importSketchName by remember { mutableStateOf("") }
    var importCode by remember { mutableStateOf("") }
    var selectedExample by remember { mutableStateOf<String?>(null) }

    // Recent projects sorted by updatedAt descending
    val recentProjects = remember(projects) {
        projects.sortedByDescending { it.updatedAt }.take(5)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Brand Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ArduinoTealDark)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Mobile Arduino IDE",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Native Microcontroller Development on Android",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ArduinoTealLight.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = ArduinoTealLight,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Workflow breadcrumb
                        Text(
                            text = "IDE WORKFLOW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArduinoTealLight,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WorkflowStepChip("1. Sketch", "sketch") { onNavigateWorkflowStep("sketch") }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            WorkflowStepChip("2. Board", "board") { onNavigateWorkflowStep("board") }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            WorkflowStepChip("3. Verify", "verify") { onNavigateWorkflowStep("verify") }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            WorkflowStepChip("4. Upload", "upload") { onNavigateWorkflowStep("upload") }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            WorkflowStepChip("5. Serial", "serial") { onNavigateWorkflowStep("serial") }
                        }
                    }
                }
            }

            // 2. Primary Actions Grid
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeActionCard(
                        title = "New Sketch",
                        subtitle = "Create clean .ino project",
                        icon = Icons.Default.Add,
                        accentColor = ArduinoTeal,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            newSketchName = "Sketch_${SimpleDateFormat("MMdd", Locale.getDefault()).format(Date())}"
                            showNewSketchDialog = true
                        }
                    )
                    HomeActionCard(
                        title = "Open Project",
                        subtitle = "Browse saved sketches",
                        icon = Icons.Default.FolderOpen,
                        accentColor = ArduinoTealLight,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAllProjects
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeActionCard(
                        title = "Import Project",
                        subtitle = "Examples or code paste",
                        icon = Icons.Default.Download,
                        accentColor = ArduinoAccentOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            importSketchName = ""
                            importCode = ""
                            selectedExample = null
                            showImportDialog = true
                        }
                    )
                    HomeActionCard(
                        title = "Settings",
                        subtitle = "Compiler & editor config",
                        icon = Icons.Default.Settings,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSettings
                    )
                }
            }

            // 3. Recent Projects Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = ArduinoTealLight, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Projects",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    TextButton(onClick = onOpenAllProjects) {
                        Text("View All (${projects.size})", fontSize = 12.sp)
                    }
                }
            }

            if (recentProjects.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No sketches created yet.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    newSketchName = "Blink"
                                    showNewSketchDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                            ) {
                                Text("Create Your First Sketch")
                            }
                        }
                    }
                }
            } else {
                items(recentProjects, key = { it.id }) { project ->
                    val isCurrent = project.id == currentProjectId
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(project.updatedAt))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProject(project.id) }
                            .testTag("recent_project_${project.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) ArduinoTeal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
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
                                            tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = ArduinoTealLight
                                            ) {
                                                Text(
                                                    text = "OPEN",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Target: ${project.selectedBoardId} • $formattedDate",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open Sketch",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dialog: Create New Sketch
        if (showNewSketchDialog) {
            AlertDialog(
                onDismissRequest = { showNewSketchDialog = false },
                title = { Text("Create New Arduino Sketch") },
                text = {
                    Column {
                        Text("Sketch Name:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newSketchName,
                            onValueChange = { newSketchName = it },
                            placeholder = { Text("e.g. LedBlinker") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArduinoTealLight)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Target Microcontroller Board:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("arduino_uno" to "Arduino Uno (ATmega328P)", "esp32" to "ESP32 Dev Module", "arduino_nano" to "Arduino Nano").forEach { (bId, bName) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedBoardId == bId) ArduinoTeal.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { selectedBoardId = bId }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selectedBoardId == bId) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedBoardId == bId) ArduinoTealLight else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newSketchName.isNotBlank()) {
                                onCreateNewSketch(newSketchName.trim(), selectedBoardId)
                                showNewSketchDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                    ) {
                        Text("Create Sketch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewSketchDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: Import Sketch
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Arduino Project") },
                text = {
                    Column {
                        Text("Choose an example or paste custom source:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedExample = "Blink"
                                    importSketchName = "Blink_Example"
                                    importCode = """// Example: LED Blink
void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
}

void loop() {
  digitalWrite(LED_BUILTIN, HIGH);
  delay(1000);
  digitalWrite(LED_BUILTIN, LOW);
  delay(1000);
}
"""
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Blink", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedExample = "SerialEcho"
                                    importSketchName = "Serial_Echo"
                                    importCode = """// Example: Serial Echo
void setup() {
  Serial.begin(115200);
  Serial.println("Arduino Serial Ready!");
}

void loop() {
  if (Serial.available() > 0) {
    char ch = Serial.read();
    Serial.print("Echo: ");
    Serial.println(ch);
  }
}
"""
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Serial", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedExample = "PWMFade"
                                    importSketchName = "PWM_Fade"
                                    importCode = """// Example: PWM LED Fade
int ledPin = 9;
int brightness = 0;
int fadeAmount = 5;

void setup() {
  pinMode(ledPin, OUTPUT);
}

void loop() {
  analogWrite(ledPin, brightness);
  brightness = brightness + fadeAmount;
  if (brightness <= 0 || brightness >= 255) {
    fadeAmount = -fadeAmount;
  }
  delay(30);
}
"""
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Fade", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = importSketchName,
                            onValueChange = { importSketchName = it },
                            label = { Text("Sketch Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = importCode,
                            onValueChange = { importCode = it },
                            label = { Text("Arduino C++ Code (.ino)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            maxLines = 8
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (importSketchName.isNotBlank() && importCode.isNotBlank()) {
                                onImportSketch(importSketchName.trim(), importCode, "arduino_uno")
                                showImportDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                    ) {
                        Text("Import & Open")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun WorkflowStepChip(title: String, tag: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("home_action_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}
