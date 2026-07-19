package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.*
import com.example.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AdventureRepository(
    private val context: Context,
    private val adventureDao: AdventureDao,
    private val logDao: AdventureLogDao,
    private val chatDao: CompanionChatDao
) {
    // Database Flow APIs
    fun getAllAdventures(): Flow<List<Adventure>> = adventureDao.getAllAdventures()
    fun getLogsForAdventure(adventureId: Int): Flow<List<AdventureLog>> = logDao.getLogsForAdventure(adventureId)
    fun getChatsForAdventure(adventureId: Int): Flow<List<CompanionChat>> = chatDao.getChatsForAdventure(adventureId)

    suspend fun getAdventureById(id: Int): Adventure? = withContext(Dispatchers.IO) {
        adventureDao.getAdventureById(id)
    }

    suspend fun updateAdventure(adventure: Adventure) = withContext(Dispatchers.IO) {
        adventureDao.updateAdventure(adventure)
    }

    suspend fun deleteAdventure(id: Int) = withContext(Dispatchers.IO) {
        val adventure = adventureDao.getAdventureById(id)
        if (adventure != null) {
            adventureDao.deleteAdventure(adventure)
            logDao.deleteLogsForAdventure(id)
            chatDao.deleteChatsForAdventure(id)
        }
    }

    suspend fun saveAdventureLog(log: AdventureLog) = withContext(Dispatchers.IO) {
        logDao.insertLog(log)
    }

    suspend fun saveCompanionChat(chat: CompanionChat) = withContext(Dispatchers.IO) {
        chatDao.insertChat(chat)
    }

    // Creates a new adventure and triggers the opening introduction scene
    suspend fun createNewAdventure(
        title: String,
        setting: String,
        characterName: String,
        characterClass: String,
        artStyle: String,
        imageSize: String
    ): Long = withContext(Dispatchers.IO) {
        val adventure = Adventure(
            title = title,
            setting = setting,
            characterName = characterName,
            characterClass = characterClass,
            artStyle = artStyle,
            imageSize = imageSize,
            inventory = "Standard Gear", // Default starting inventory
            currentQuest = "Begin the journey"
        )
        adventureDao.insertAdventure(adventure)
    }

    /**
     * Calls Gemini 3.1 Pro to generate the next state of the adventure.
     * Incorporates previous history for continuity.
     */
    suspend fun generateNextStoryState(
        adventure: Adventure,
        logs: List<AdventureLog>,
        userChoice: String,
        apiKey: String
    ): StoryStateResult = withContext(Dispatchers.IO) {
        val model = "gemini-3.1-pro-preview"

        // Build history context
        val historyBuilder = StringBuilder()
        historyBuilder.append("Setting: ${adventure.setting}\n")
        historyBuilder.append("Protagonist Name: ${adventure.characterName}\n")
        historyBuilder.append("Protagonist Class: ${adventure.characterClass}\n")
        historyBuilder.append("Current Quest: ${adventure.currentQuest}\n")
        historyBuilder.append("Current Inventory: ${adventure.inventory}\n\n")
        historyBuilder.append("Adventure Log History:\n")

        logs.forEach { log ->
            if (log.role == "choice_made") {
                historyBuilder.append("> Action Taken: ${log.text}\n")
            } else {
                historyBuilder.append("${log.text}\n")
            }
        }

        if (userChoice.isNotEmpty()) {
            historyBuilder.append("\nNew User Action/Choice: $userChoice\n")
        } else {
            historyBuilder.append("\nThis is the beginning of the story. Generate the opening scene!\n")
        }

        val systemInstruction = """
            You are an elite, highly creative Dungeon Master. You are writing an immersive, infinite choose-your-own-adventure game.
            The choices made by the player must genuinely alter the upcoming plot, causing significant narrative shifts, encounters, and consequences.
            
            You must reply ONLY with a single JSON object. Do not include any formatting or conversational text outside the JSON.
            The JSON structure must match this schema exactly:
            {
              "title": "A short, engaging chapter or scene title",
              "story": "The next segment of the adventure. Be highly descriptive, pacing the narrative perfectly. Reference current inventory or quests if relevant.",
              "options": [
                "Detailed, active Choice A",
                "Detailed, active Choice B",
                "Detailed, active Choice C"
              ],
              "quest_update": "The active main quest description (updated if the quest changes, or kept the same if still on it)",
              "inventory_add": ["Item to add"], // list of string items player finds/gains, empty list if none
              "inventory_remove": ["Item to remove"], // list of string items used/lost, empty list if none
              "visual_prompt": "A highly detailed, style-neutral visual prompt for an image generator illustrating this scene (e.g., 'An elven warrior standing on a mossy cliff overlooking a steaming volcanic caldera, holding a glowing blue orb'). Do NOT include style terms like 'watercolor' or 'pixel art'."
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = historyBuilder.toString())))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 1.0f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = RetrofitClient.service.generateContent(model, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No response received from story engine.")

            // Strip potential markdown wrappers if any
            val cleanJson = sanitizeJsonString(jsonText)
            val jsonObj = JSONObject(cleanJson)

            val title = jsonObj.getString("title")
            val story = jsonObj.getString("story")

            val optionsArray = jsonObj.getJSONArray("options")
            val options = mutableListOf<String>()
            for (i in 0 until optionsArray.length()) {
                options.add(optionsArray.getString(i))
            }

            val questUpdate = jsonObj.optString("quest_update", adventure.currentQuest)

            val addArray = jsonObj.optJSONArray("inventory_add")
            val itemsToAdd = mutableListOf<String>()
            if (addArray != null) {
                for (i in 0 until addArray.length()) {
                    itemsToAdd.add(addArray.getString(i))
                }
            }

            val removeArray = jsonObj.optJSONArray("inventory_remove")
            val itemsToRemove = mutableListOf<String>()
            if (removeArray != null) {
                for (i in 0 until removeArray.length()) {
                    itemsToRemove.add(removeArray.getString(i))
                }
            }

            val visualPrompt = jsonObj.getString("visual_prompt")

            StoryStateResult.Success(
                title = title,
                story = story,
                options = options,
                questUpdate = questUpdate,
                itemsToAdd = itemsToAdd,
                itemsToRemove = itemsToRemove,
                visualPrompt = visualPrompt
            )
        } catch (e: Exception) {
            StoryStateResult.Error(e.message ?: "Failed to generate story progression.")
        }
    }

    /**
     * Calls Gemini 3.1 Flash Lite (low-latency) to respond to the player's companion chat.
     */
    suspend fun generateCompanionResponse(
        adventure: Adventure,
        chats: List<CompanionChat>,
        userMessage: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val model = "gemini-3.1-flash-lite-preview"

        val systemInstruction = """
            You are 'Astra the AI Companion', a loyal, slightly sarcastic, and extremely observant spirit guide who travels with the player.
            The current setting is '${adventure.setting}'.
            The player is '${adventure.characterName}', a '${adventure.characterClass}'.
            Their current Quest is: '${adventure.currentQuest}'.
            Their current Inventory is: '${adventure.inventory}'.
            
            You know everything that has occurred, and you should react with witty, supportive, or cautious remarks based on the situation.
            Keep your responses punchy, concise, immersive, and fully in-character. Do not break character under any circumstances.
        """.trimIndent()

        val chatContents = mutableListOf<Content>()
        chats.takeLast(10).forEach { chat ->
            val role = if (chat.sender == "user") "user" else "model"
            chatContents.add(Content(parts = listOf(Part(text = chat.message))))
        }
        // Add the new user message
        chatContents.add(Content(parts = listOf(Part(text = userMessage))))

        val request = GenerateContentRequest(
            contents = chatContents,
            generationConfig = GenerationConfig(temperature = 0.8f),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = RetrofitClient.service.generateContent(model, apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I am here, but I found myself lost in silence."
        } catch (e: Exception) {
            "Companion connection interrupted: ${e.message}"
        }
    }

    /**
     * Calls Gemini 3 Pro Image (gemini-3-pro-image-preview) to generate a high-quality illustration.
     * Appends the adventure's consistent Art Style, saves the resulting image to a local file,
     * and returns the file path.
     */
    suspend fun generateSceneImage(
        prompt: String,
        artStyle: String,
        imageSize: String, // "1K", "2K", "4K"
        apiKey: String
    ): String? = withContext(Dispatchers.IO) {
        val model = "gemini-3-pro-image-preview"

        // Combine the descriptive scene prompt with the consistent art style prompt
        val fullyStyledPrompt = "$artStyle style: $prompt. Epic lighting, masterfully composed, detailed scene, fitting the art style perfectly."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = fullyStyledPrompt)))),
            generationConfig = GenerationConfig(
                imageConfig = ImageConfig(aspectRatio = "16:9", imageSize = imageSize),
                responseModalities = listOf("TEXT", "IMAGE")
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(model, apiKey, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            val inlineData = parts?.firstOrNull { it.inlineData != null }?.inlineData

            if (inlineData != null) {
                // Decode Base64 and save to local filesDir
                val decodedBytes = Base64.decode(inlineData.data, Base64.DEFAULT)
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(decodedBytes)
                }
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sanitizeJsonString(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.substringAfter("```").substringBeforeLast("```").trim()
        }
        return clean
    }
}

sealed class StoryStateResult {
    data class Success(
        val title: String,
        val story: String,
        val options: List<String>,
        val questUpdate: String,
        val itemsToAdd: List<String>,
        val itemsToRemove: List<String>,
        val visualPrompt: String
    ) : StoryStateResult()

    data class Error(val message: String) : StoryStateResult()
}
