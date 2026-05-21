package com.example.data.dao

import androidx.room.*
import com.example.data.entity.CardTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CardTransactionDao {
    @Query("SELECT * FROM card_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<CardTransaction>>

    @Query("SELECT * FROM card_transactions WHERE cardId = :cardId ORDER BY timestamp DESC")
    fun getTransactionsForCard(cardId: String): Flow<List<CardTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: CardTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: CardTransaction)

    @Query("SELECT SUM(amount) FROM card_transactions WHERE cardId = :cardId")
    fun getTotalExpenditureForCardFlow(cardId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM card_transactions WHERE cardId = :cardId")
    suspend fun getTotalExpenditureForCard(cardId: String): Double?

    @Query("SELECT SUM(amount) FROM card_transactions")
    fun getTotalOverallExpenditureFlow(): Flow<Double?>
}
