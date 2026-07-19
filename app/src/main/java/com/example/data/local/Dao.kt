package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AdventureDao {
    @Query("SELECT * FROM adventures ORDER BY timestamp DESC")
    fun getAllAdventures(): Flow<List<Adventure>>

    @Query("SELECT * FROM adventures WHERE id = :id")
    suspend fun getAdventureById(id: Int): Adventure?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdventure(adventure: Adventure): Long

    @Update
    suspend fun updateAdventure(adventure: Adventure)

    @Delete
    suspend fun deleteAdventure(adventure: Adventure)
}

@Dao
interface AdventureLogDao {
    @Query("SELECT * FROM adventure_logs WHERE adventureId = :adventureId ORDER BY timestamp ASC")
    fun getLogsForAdventure(adventureId: Int): Flow<List<AdventureLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AdventureLog): Long

    @Query("DELETE FROM adventure_logs WHERE adventureId = :adventureId")
    suspend fun deleteLogsForAdventure(adventureId: Int)
}

@Dao
interface CompanionChatDao {
    @Query("SELECT * FROM companion_chats WHERE adventureId = :adventureId ORDER BY timestamp ASC")
    fun getChatsForAdventure(adventureId: Int): Flow<List<CompanionChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: CompanionChat): Long

    @Query("DELETE FROM companion_chats WHERE adventureId = :adventureId")
    suspend fun deleteChatsForAdventure(adventureId: Int)
}
