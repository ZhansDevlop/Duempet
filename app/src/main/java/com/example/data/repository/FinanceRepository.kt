package com.example.data.repository

import com.example.data.dao.NfcCardDao
import com.example.data.dao.CardTransactionDao
import com.example.data.entity.NfcCard
import com.example.data.entity.CardTransaction
import kotlinx.coroutines.flow.Flow

class FinanceRepository(
    private val nfcCardDao: NfcCardDao,
    private val cardTransactionDao: CardTransactionDao
) {
    val allCards: Flow<List<NfcCard>> = nfcCardDao.getAllCards()
    val allTransactions: Flow<List<CardTransaction>> = cardTransactionDao.getAllTransactions()
    val totalExpenditureOverall: Flow<Double?> = cardTransactionDao.getTotalOverallExpenditureFlow()

    suspend fun getCardById(id: String): NfcCard? {
        return nfcCardDao.getCardById(id)
    }

    suspend fun insertOrUpdateCard(card: NfcCard) {
        nfcCardDao.insertCard(card)
    }

    suspend fun deleteCard(card: NfcCard) {
        nfcCardDao.deleteCard(card)
    }

    suspend fun updateBalance(cardId: String, newBalance: Double) {
        nfcCardDao.updateBalanceAndTappedTime(cardId, newBalance, System.currentTimeMillis())
    }

    fun getTransactionsForCard(cardId: String): Flow<List<CardTransaction>> {
        return cardTransactionDao.getTransactionsForCard(cardId)
    }

    fun getTotalExpenditureForCardFlow(cardId: String): Flow<Double?> {
        return cardTransactionDao.getTotalExpenditureForCardFlow(cardId)
    }

    suspend fun addTransaction(transaction: CardTransaction) {
        cardTransactionDao.insertTransaction(transaction)
        val card = nfcCardDao.getCardById(transaction.cardId)
        if (card != null) {
            val newBalance = card.balance - transaction.amount
            nfcCardDao.updateBalanceAndTappedTime(card.id, newBalance, System.currentTimeMillis())
        }
    }

    suspend fun deleteTransaction(transaction: CardTransaction) {
        cardTransactionDao.deleteTransaction(transaction)
        val card = nfcCardDao.getCardById(transaction.cardId)
        if (card != null) {
            val newBalance = card.balance + transaction.amount
            nfcCardDao.updateBalanceAndTappedTime(card.id, newBalance, System.currentTimeMillis())
        }
    }
}
