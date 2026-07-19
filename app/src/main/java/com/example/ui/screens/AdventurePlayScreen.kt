package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.Adventure
import com.example.data.local.AdventureLog
import com.example.data.local.CompanionChat
import com.example.ui.viewmodel.AdventureViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdventurePlayScreen(
    viewModel: AdventureViewModel,
    onBack: () -> Unit
) {
    val adventure by viewModel.currentAdventure.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCompanionLoading by viewModel.isCompanionLoading.collectAsState()
    val isGeneratingImage by viewModel.isGeneratingImage.collectAsState()
    val generationStatus by viewModel.generationStatus.collectAsState()
    val error by viewModel.error.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Story, 1: Companion, 2: HUD

    val currentAdv = adventure ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentAdv.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Playing as ${currentAdv.characterName} the ${currentAdv.characterClass}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isLoading || isCompanionLoading || isGeneratingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isWideScreen = maxWidth > 600.dp

            if (isWideScreen) {
                // Dual pane layout for tablets / landscape
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column (60%): Story Scroll & Action Choice
                    Box(modifier = Modifier.weight(0.6f)) {
                        StoryColumn(
                            logs = logs,
                            isLoading = isLoading,
                            generationStatus = generationStatus,
                            onChoiceMade = { choice -> viewModel.makeChoice(choice) }
                        )
                    }

                    // Right Column (40%): Live Sidebar
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        SidebarContent(
                            adventure = currentAdv,
                            chats = chats,
                            isCompanionLoading = isCompanionLoading,
                            isGeneratingImage = isGeneratingImage,
                            onSendChat = { msg -> viewModel.sendCompanionMessage(msg) }
                        )
                    }
                }
            } else {
                // Vertical layout for phones with Navigation tabs
                Column(modifier = Modifier.fillMaxSize()) {
                    // Tab Row
                    TabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            icon = { Icon(Icons.Default.Book, contentDescription = null) },
                            text = { Text("Story") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                            text = { Text("Companion") }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            icon = { Icon(Icons.Default.Backpack, contentDescription = null) },
                            text = { Text("Inventory") }
                        )
                    }

                    // Content based on tab Selection
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            0 -> StoryColumn(
                                logs = logs,
                                isLoading = isLoading,
                                generationStatus = generationStatus,
                                onChoiceMade = { choice -> viewModel.makeChoice(choice) }
                            )
                            1 -> CompanionChatTab(
                                chats = chats,
                                isCompanionLoading = isCompanionLoading,
                                onSendChat = { msg -> viewModel.sendCompanionMessage(msg) }
                            )
                            2 -> InventoryHudTab(
                                adventure = currentAdv,
                                isGeneratingImage = isGeneratingImage
                            )
                        }
                    }
                }
            }

            // Error snackbar/dialog
            if (error != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Adventure Disturbance") },
                    text = { Text(error ?: "An unknown anomaly occurred.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Acknowledge")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StoryColumn(
    logs: List<AdventureLog>,
    isLoading: Boolean,
    generationStatus: String,
    onChoiceMade: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var customActionText by remember { mutableStateOf("") }

    // Scroll to the latest story segment automatically when it updates
    LaunchedEffect(logs.size, isLoading) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(logs) { log ->
                LogItemRow(log = log)
            }

            if (isLoading) {
                item {
                    LoadingStoryIndicator(statusText = generationStatus)
                }
            }
        }

        // Action controls (only show choices if we are not loading the next segment)
        if (!isLoading && logs.isNotEmpty()) {
            val lastLog = logs.lastOrNull { it.role == "story" }
            if (lastLog != null) {
                val choices = remember(lastLog.text) {
                    parseChoicesFromText(lastLog.text)
                }

                Surface(
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "What is your next move?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Generated structured options
                        choices.forEachIndexed { index, choice ->
                            Button(
                                onClick = { onChoiceMade(choice) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("choice_option_$index"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(
                                    text = choice,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Text Field for open-ended custom choices
                        OutlinedTextField(
                            value = customActionText,
                            onValueChange = { customActionText = it },
                            placeholder = { Text("Or write your own custom action...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_action_input"),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (customActionText.isNotBlank()) {
                                            onChoiceMade(customActionText)
                                            customActionText = ""
                                            focusManager.clearFocus()
                                        }
                                    },
                                    enabled = customActionText.isNotBlank()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                }
                            },
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: AdventureLog) {
    if (log.role == "choice_made") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = "> ${log.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    } else {
        // Narrative Log Row with potential real-time image
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scene Graphic Illustration (real-time loaded)
            if (log.imagePath != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = File(log.imagePath),
                        contentDescription = "Real-time illustration of scene",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Narrative Story Block
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val cleanText = remember(log.text) {
                        stripChoicesLabel(log.text)
                    }
                    FormattedStoryText(cleanText)
                }
            }
        }
    }
}

@Composable
fun FormattedStoryText(text: String) {
    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("### ")) {
                Text(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (trimmed.startsWith("**") && trimmed.endsWith("**")) {
                Text(
                    text = trimmed.replace("**", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else if (trimmed.isNotEmpty()) {
                Text(
                    text = trimmed,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
fun LoadingStoryIndicator(statusText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SidebarContent(
    adventure: Adventure,
    chats: List<CompanionChat>,
    isCompanionLoading: Boolean,
    isGeneratingImage: Boolean,
    onSendChat: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HUD Inventory & Quest
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quest row
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = "Current Quest",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Current Quest",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            adventure.currentQuest,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Inventory row
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Backpack,
                        contentDescription = "Inventory",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Inventory Items",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            adventure.inventory,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (isGeneratingImage) {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                        Text(
                            "Illustrator generating real-time image...",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Interactive Companion Chat Frame
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            CompanionChatTab(
                chats = chats,
                isCompanionLoading = isCompanionLoading,
                onSendChat = onSendChat
            )
        }
    }
}

@Composable
fun CompanionChatTab(
    chats: List<CompanionChat>,
    isCompanionLoading: Boolean,
    onSendChat: (String) -> Unit
) {
    val chatListState = rememberLazyListState()
    var chatMessageText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(chats.size, isCompanionLoading) {
        if (chats.isNotEmpty()) {
            chatListState.animateScrollToItem(chats.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Chat Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Talk to Astra (AI Spirit Guide)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Chat Message Thread
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chats) { chat ->
                val isModel = chat.sender == "companion"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
                ) {
                    Surface(
                        color = if (isModel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isModel) 0.dp else 12.dp,
                            bottomEnd = if (isModel) 12.dp else 0.dp
                        ),
                        modifier = Modifier.widthIn(max = 240.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = chat.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isModel) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (isCompanionLoading) {
                item {
                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp)
                                Text("Astra is typing...", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chat Input box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = chatMessageText,
                onValueChange = { chatMessageText = it },
                placeholder = { Text("Ask for help, strategies...", fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("companion_chat_input"),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (chatMessageText.isNotBlank()) {
                                onSendChat(chatMessageText)
                                chatMessageText = ""
                                focusManager.clearFocus()
                            }
                        },
                        enabled = chatMessageText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to Astra")
                    }
                },
                singleLine = true
            )
        }
    }
}

@Composable
fun InventoryHudTab(adventure: Adventure, isGeneratingImage: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Current Active Quest",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(adventure.currentQuest, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Protagonist Backpack",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(adventure.inventory, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (isGeneratingImage) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("AI Artist is generating the current scene image...")
                }
            }
        }
    }
}

// Helpers for splitting choices from generated story logs
private fun parseChoicesFromText(text: String): List<String> {
    if (!text.contains("**Choices:**")) {
        return emptyList()
    }
    val choicesPart = text.substringAfter("**Choices:**").trim()
    return choicesPart.split("\n")
        .map { it.trim().removePrefix("-").removePrefix(" -").trim() }
        .filter { it.isNotEmpty() }
}

private fun stripChoicesLabel(text: String): String {
    if (text.contains("**Choices:**")) {
        return text.substringBefore("**Choices:**").trim()
    }
    return text
}
