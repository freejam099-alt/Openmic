package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.Adventure
import com.example.ui.viewmodel.AdventureViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AdventureViewModel,
    onAdventureSelected: (Int) -> Unit
) {
    val adventures by viewModel.adventures.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "CYOA Adventure",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Infinite Choose-Your-Own-Adventure Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_adventure_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Adventure")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Adventure", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (adventures.isEmpty()) {
                EmptyState(onNewAdventureClick = { showCreateDialog = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Your Saved Chronicles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(adventures) { adventure ->
                        AdventureCard(
                            adventure = adventure,
                            onClick = { onAdventureSelected(adventure.id) },
                            onDelete = { viewModel.deleteAdventure(adventure.id) }
                        )
                    }
                }
            }

            // Dialog to start new adventure
            if (showCreateDialog) {
                CreateAdventureDialog(
                    onDismiss = { showCreateDialog = false },
                    onCreate = { title, setting, charName, charClass, artStyle, imageSize ->
                        showCreateDialog = false
                        viewModel.startNewAdventure(title, setting, charName, charClass, artStyle, imageSize)
                    }
                )
            }

            // Dialog to configure API Key
            if (showSettingsDialog) {
                SettingsDialog(
                    currentKey = apiKey,
                    onDismiss = { showSettingsDialog = false },
                    onSave = { newKey ->
                        viewModel.setCustomApiKey(newKey)
                        showSettingsDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyState(onNewAdventureClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chronicles Unwritten",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Embark on custom narrative journeys generated in real-time. Define your protagonist, choose an art style, and let the adventure unfold.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNewAdventureClick,
            modifier = Modifier.testTag("empty_state_create_button")
        ) {
            Text("Begin Your First Chronicle", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdventureCard(
    adventure: Adventure,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(adventure.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault())
        sdf.format(Date(adventure.timestamp))
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adventure_card_${adventure.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = adventure.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(adventure.setting, fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${adventure.characterName} (${adventure.characterClass})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Art: ${adventure.artStyle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last Played: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_adventure_${adventure.id}")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete adventure",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAdventureDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, setting: String, charName: String, charClass: String, artStyle: String, imageSize: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var charName by remember { mutableStateOf("") }
    
    val settings = listOf("High Fantasy", "Cyberpunk Megacity", "Eldritch Sea", "Steampunk Sky", "Nuclear Wasteland")
    var selectedSetting by remember { mutableStateOf(settings[0]) }

    val classes = listOf("Warrior", "Mage", "Rogue", "Cleric", "Engineer", "Survivor")
    var selectedClass by remember { mutableStateOf(classes[0]) }

    val artStyles = listOf(
        "Gothic Fantasy Oil Painting",
        "Cyberpunk Pixel Art",
        "Watercolor Anime Illustration",
        "Comic Book Detective Noir",
        "Dreamy Surrealist Digital"
    )
    var selectedArtStyle by remember { mutableStateOf(artStyles[0]) }

    val imageSizes = listOf("1K", "2K", "4K")
    var selectedImageSize by remember { mutableStateOf(imageSizes[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Forge Your Chronicle",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Adventure Title") },
                        placeholder = { Text("e.g. The Crypts of Moria") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_adventure_title"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = charName,
                        onValueChange = { charName = it },
                        label = { Text("Protagonist Name") },
                        placeholder = { Text("e.g. Elara Windrunner") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_char_name"),
                        singleLine = true
                    )
                }

                item {
                    Text("Genre Setting", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        settings.forEach { setting ->
                            FilterChip(
                                selected = selectedSetting == setting,
                                onClick = { selectedSetting = setting },
                                label = { Text(setting) }
                            )
                        }
                    }
                }

                item {
                    Text("Character Archetype", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        classes.forEach { charClass ->
                            FilterChip(
                                selected = selectedClass == charClass,
                                onClick = { selectedClass = charClass },
                                label = { Text(charClass) }
                            )
                        }
                    }
                }

                item {
                    Text("Consistent Art Style", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        artStyles.forEach { style ->
                            FilterChip(
                                selected = selectedArtStyle == style,
                                onClick = { selectedArtStyle = style },
                                label = { Text(style) }
                            )
                        }
                    }
                }

                item {
                    Text("Real-time Image Size", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageSizes.forEach { size ->
                            FilterChip(
                                selected = selectedImageSize == size,
                                onClick = { selectedImageSize = size },
                                label = { Text(size) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.ifBlank { "$selectedSetting Quest" }
                    val finalName = charName.ifBlank { "The Adventurer" }
                    onCreate(finalTitle, selectedSetting, finalName, selectedClass, selectedArtStyle, selectedImageSize)
                },
                enabled = true,
                modifier = Modifier.testTag("dialog_create_button")
            ) {
                Text("Embark")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        content = { content() }
    )
}

@Composable
fun SettingsDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You can override the Gemini API Key used by the adventure engine here.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key) },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
