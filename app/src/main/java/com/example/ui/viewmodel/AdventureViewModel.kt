package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.*
import com.example.data.repository.AdventureRepository
import com.example.data.repository.StoryStateResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdventureViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AdventureRepository(
        application,
        db.adventureDao(),
        db.adventureLogDao(),
        db.companionChatDao()
    )

    // All adventures list for dashboard
    val adventures: StateFlow<List<Adventure>> = repository.getAllAdventures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active adventure state
    private val _currentAdventure = MutableStateFlow<Adventure?>(null)
    val currentAdventure: StateFlow<Adventure?> = _currentAdventure.asStateFlow()

    // Logs for active adventure
    private val _logs = MutableStateFlow<List<AdventureLog>>(emptyList())
    val logs: StateFlow<List<AdventureLog>> = _logs.asStateFlow()

    // Companion chats for active adventure
    private val _chats = MutableStateFlow<List<CompanionChat>>(emptyList())
    val chats: StateFlow<List<CompanionChat>> = _chats.asStateFlow()

    // UI States
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCompanionLoading = MutableStateFlow(false)
    val isCompanionLoading: StateFlow<Boolean> = _isCompanionLoading.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _generationStatus = MutableStateFlow("")
    val generationStatus: StateFlow<String> = _generationStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // API Key (defaults to BuildConfig value, can be overridden by user)
    private val _apiKey = MutableStateFlow(BuildConfig.GEMINI_API_KEY)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        // Log API key presence for troubleshooting
        if (_apiKey.value.isEmpty()) {
            Log.e("AdventureViewModel", "Gemini API Key is empty in BuildConfig!")
        }
    }

    fun setCustomApiKey(key: String) {
        _apiKey.value = key
    }

    fun clearError() {
        _error.value = null
    }

    fun resetActiveAdventure() {
        _currentAdventure.value = null
        _logs.value = emptyList()
        _chats.value = emptyList()
    }

    // Load an existing adventure from database and collect its flows
    fun loadAdventure(adventureId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val adventure = repository.getAdventureById(adventureId)
            if (adventure != null) {
                _currentAdventure.value = adventure
                
                // Collect logs flow
                repository.getLogsForAdventure(adventureId).collectLatest { logsList ->
                    _logs.value = logsList
                    
                    // If this is a newly created adventure with no logs yet, generate the introduction
                    if (logsList.isEmpty()) {
                        generateIntro(adventure)
                    }
                }
            } else {
                _error.value = "Failed to load adventure with ID $adventureId"
            }
            _isLoading.value = false
        }

        // Also collect chats separately
        viewModelScope.launch {
            repository.getChatsForAdventure(adventureId).collectLatest { chatsList ->
                _chats.value = chatsList
            }
        }
    }

    // Start a brand-new adventure
    fun startNewAdventure(
        title: String,
        setting: String,
        characterName: String,
        characterClass: String,
        artStyle: String,
        imageSize: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val newId = repository.createNewAdventure(
                    title = title,
                    setting = setting,
                    characterName = characterName,
                    characterClass = characterClass,
                    artStyle = artStyle,
                    imageSize = imageSize
                )
                loadAdventure(newId.toInt())
            } catch (e: Exception) {
                _error.value = "Failed to create adventure: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Generate the opening introduction scene for a newly started adventure
    private suspend fun generateIntro(adventure: Adventure) {
        _isLoading.value = true
        _generationStatus.value = "Consulting the Elder Fates..."
        
        val key = _apiKey.value
        if (key.isEmpty()) {
            _error.value = "API Key is missing. Please configure it in Settings."
            _isLoading.value = false
            return
        }

        val result = repository.generateNextStoryState(adventure, emptyList(), userChoice = "", apiKey = key)
        handleStoryResult(adventure, result, key)
    }

    // Handle user selecting an option or typing a custom action
    fun makeChoice(choiceText: String) {
        val adventure = _currentAdventure.value ?: return
        val currentLogs = _logs.value
        val key = _apiKey.value

        if (key.isEmpty()) {
            _error.value = "API Key is missing. Please configure it in Settings."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _generationStatus.value = "Processing choice..."

            // 1. Save user action log locally
            val userChoiceLog = AdventureLog(
                adventureId = adventure.id,
                role = "choice_made",
                text = choiceText
            )
            repository.saveAdventureLog(userChoiceLog)

            // 2. Call story generator with new context
            val result = repository.generateNextStoryState(adventure, currentLogs + userChoiceLog, userChoice = choiceText, apiKey = key)
            handleStoryResult(adventure, result, key)
        }
    }

    // Helper to process story results, update adventure parameters, and spawn image generation
    private suspend fun handleStoryResult(adventure: Adventure, result: StoryStateResult, key: String) {
        when (result) {
            is StoryStateResult.Success -> {
                // Parse and update inventory
                val updatedInventory = parseUpdatedInventory(adventure.inventory, result.itemsToAdd, result.itemsToRemove)
                
                // Update adventure state in Room
                val updatedAdventure = adventure.copy(
                    currentQuest = result.questUpdate,
                    inventory = updatedInventory,
                    timestamp = System.currentTimeMillis()
                )
                repository.updateAdventure(updatedAdventure)
                _currentAdventure.value = updatedAdventure

                // Format story text with its Chapter Title in bold
                val fullNarrative = "### ${result.title}\n\n${result.story}\n\n" +
                        "**Choices:**\n" +
                        result.options.mapIndexed { idx, opt -> " - $opt" }.joinToString("\n")

                // Save story log (temporarily with no image)
                val storyLogId = repository.saveAdventureLog(
                    AdventureLog(
                        adventureId = adventure.id,
                        role = "story",
                        text = fullNarrative,
                        imagePath = null
                    )
                )

                _isLoading.value = false // Story text is loaded, show to player immediately

                // Trigger scene image generation asynchronously so user can read while it paints
                generateImageForLog(storyLogId.toInt(), result.visualPrompt, adventure.artStyle, adventure.imageSize, key)
            }
            is StoryStateResult.Error -> {
                _error.value = result.message
                _isLoading.value = false
            }
        }
    }

    // Perform scene image generation, save it offline, and link to its log item
    private fun generateImageForLog(
        logId: Int,
        prompt: String,
        artStyle: String,
        imageSize: String,
        apiKey: String
    ) {
        viewModelScope.launch {
            _isGeneratingImage.value = true
            _generationStatus.value = "Painting world illustrations..."
            try {
                val imagePath = repository.generateSceneImage(prompt, artStyle, imageSize, apiKey)
                if (imagePath != null) {
                    // Update log item in Room with the image path
                    val logItem = _logs.value.find { it.id == logId }
                    if (logItem != null) {
                        repository.saveAdventureLog(logItem.copy(imagePath = imagePath))
                    }
                }
            } catch (e: Exception) {
                Log.e("AdventureViewModel", "Image generation failed: ${e.message}")
            } finally {
                _isGeneratingImage.value = false
                _generationStatus.value = ""
            }
        }
    }

    // Send multi-turn companion chat
    fun sendCompanionMessage(message: String) {
        val adventure = _currentAdventure.value ?: return
        val currentChats = _chats.value
        val key = _apiKey.value

        if (message.isBlank()) return
        if (key.isEmpty()) {
            _error.value = "API Key is missing."
            return
        }

        viewModelScope.launch {
            _isCompanionLoading.value = true
            try {
                // Save user message in local DB
                val userChat = CompanionChat(
                    adventureId = adventure.id,
                    sender = "user",
                    message = message
                )
                repository.saveCompanionChat(userChat)

                // Call low-latency model for response
                val response = repository.generateCompanionResponse(
                    adventure = adventure,
                    chats = currentChats + userChat,
                    userMessage = message,
                    apiKey = key
                )

                // Save companion response in local DB
                repository.saveCompanionChat(
                    CompanionChat(
                        adventureId = adventure.id,
                        sender = "companion",
                        message = response
                    )
                )
            } catch (e: Exception) {
                _error.value = "Companion failed to respond: ${e.message}"
            } finally {
                _isCompanionLoading.value = false
            }
        }
    }

    // Delete a historic adventure
    fun deleteAdventure(adventureId: Int) {
        viewModelScope.launch {
            repository.deleteAdventure(adventureId)
            if (_currentAdventure.value?.id == adventureId) {
                resetActiveAdventure()
            }
        }
    }

    // Helper logic to parse additions/removals for inventory list
    private fun parseUpdatedInventory(
        currentInv: String,
        toAdd: List<String>,
        toRemove: List<String>
    ): String {
        val list = currentInv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "Standard Gear" }
            .toMutableList()

        toAdd.forEach { item ->
            if (item.isNotBlank() && !list.contains(item)) {
                list.add(item)
            }
        }

        toRemove.forEach { item ->
            list.remove(item)
        }

        if (list.isEmpty()) {
            return "Nothing but your wits"
        }
        return list.joinToString(", ")
    }
}
