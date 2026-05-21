package com.example.data.dao

import androidx.room.*
import com.example.data.entity.NfcCard
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcCardDao {
    @Query("SELECT * FROM nfc_cards ORDER BY lastTappedTime DESC")
    fun getAllCards(): Flow<List<NfcCard>>

    @Query("SELECT * FROM nfc_cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: String): NfcCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: NfcCard)

    @Update
    suspend fun updateCard(card: NfcCard)

    @Delete
    suspend fun deleteCard(card: NfcCard)

    @Query("UPDATE nfc_cards SET balance = :newBalance, lastTappedTime = :tappedTime WHERE id = :cardId")
    suspend fun updateBalanceAndTappedTime(cardId: String, newBalance: Double, tappedTime: Long)
}
