package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adventures")
data class Adventure(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val setting: String,
    val characterName: String,
    val characterClass: String,
    val artStyle: String,
    val imageSize: String, // "1K", "2K", "4K"
    val inventory: String, // Comma-separated or JSON list of items
    val currentQuest: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)

@Entity(tableName = "adventure_logs")
data class AdventureLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val adventureId: Int,
    val role: String, // "story", "choice_made"
    val text: String,
    val imagePath: String? = null, // Path to local stored generated image
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "companion_chats")
data class CompanionChat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val adventureId: Int,
    val sender: String, // "user", "companion"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
