package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.CardTransaction
import com.example.data.entity.NfcCard
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface NfcScanState {
    object Idle : NfcScanState
    object Scanning : NfcScanState
    data class Success(val card: NfcCard, val isNew: Boolean) : NfcScanState
    data class Error(val message: String) : NfcScanState
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(database.nfcCardDao(), database.cardTransactionDao())

    val cards: StateFlow<List<NfcCard>> = repository.allCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transactions: StateFlow<List<CardTransaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalExpenditureOverall: StateFlow<Double> = repository.totalExpenditureOverall
        .map { it ?: 0.0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    private val _selectedCardId = MutableStateFlow<String?>(null)
    val selectedCardId: StateFlow<String?> = _selectedCardId.asStateFlow()

    val selectedCard: StateFlow<NfcCard?> = combine(cards, selectedCardId) { currentCards, selectedId ->
        if (selectedId == null) {
            currentCards.firstOrNull()
        } else {
            currentCards.find { it.id == selectedId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedCardTransactions: StateFlow<List<CardTransaction>> = combine(selectedCardId, transactions) { selectedId, allTx ->
        val effectiveId = selectedId ?: cards.value.firstOrNull()?.id
        if (effectiveId != null) {
            allTx.filter { it.cardId == effectiveId }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanState = MutableStateFlow<NfcScanState>(NfcScanState.Idle)
    val scanState: StateFlow<NfcScanState> = _scanState.asStateFlow()

    init {
        // Seed database if it's empty on load
        viewModelScope.launch {
            cards.firstOrNull() // Trigger flow subscription
            // Let a short delay ensure lists fetch
            kotlinx.coroutines.delay(100)
            if (cards.value.isEmpty()) {
                seedDemoData()
            } else {
                _selectedCardId.value = cards.value.firstOrNull()?.id
            }
        }
    }

    private suspend fun seedDemoData() {
        val now = System.currentTimeMillis()
        val flazzCard = NfcCard(
            id = "FLAZZ_982736152",
            name = "BCA Flazz Utama",
            type = "BCA Flazz",
            balance = 142500.0,
            lastTappedTime = now - 7200000, // 2 hours ago
            cardColorHex = "#1e3c72,#2a5298" // Deep BCA blue gradient
        )
        val emoneyCard = NfcCard(
            id = "EM_MANDIRI_88273",
            name = "E-Money Mandiri Tol",
            type = "Mandiri e-Money",
            balance = 85000.0,
            lastTappedTime = now - 18000000, // 5 hours ago
            cardColorHex = "#0F2027,#203A43,#2C5364" // Charcoal teal gradient
        )
        val brizziCard = NfcCard(
            id = "BRI_BRIZZI_47219",
            name = "Brizzi BRI Transit",
            type = "BRI Brizzi",
            balance = 230000.0,
            lastTappedTime = now - 86400000, // 1 day ago
            cardColorHex = "#134E5E,#71B280" // Forest green-blue gradient
        )

        repository.insertOrUpdateCard(flazzCard)
        repository.insertOrUpdateCard(emoneyCard)
        repository.insertOrUpdateCard(brizziCard)

        // Seed transactions
        repository.addTransaction(
            CardTransaction(
                cardId = flazzCard.id,
                amount = 21000.0,
                category = "Tol & Parkir",
                note = "Gerbang Tol Cilandak",
                timestamp = now - 10800000
            )
        )
        repository.addTransaction(
            CardTransaction(
                cardId = flazzCard.id,
                amount = 14000.0,
                category = "Transportasi",
                note = "MRT Fatmawati ke Blok M",
                timestamp = now - 14400000
            )
        )
        repository.addTransaction(
            CardTransaction(
                cardId = flazzCard.id,
                amount = 22500.0,
                category = "Makanan",
                note = "Kopi Susu Gula Aren",
                timestamp = now - 21600000
            )
        )

        // Mandiri Trans
        repository.addTransaction(
            CardTransaction(
                cardId = emoneyCard.id,
                amount = 50000.0,
                category = "Tol & Parkir",
                note = "Tol Cipularang KM 72",
                timestamp = now - 25000000
            )
        )
        repository.addTransaction(
            CardTransaction(
                cardId = emoneyCard.id,
                amount = 35000.0,
                category = "Supermarket",
                note = "Minyak Wangi & Camilan",
                timestamp = now - 30000000
            )
        )

        // Brizzi Trans
        repository.addTransaction(
            CardTransaction(
                cardId = brizziCard.id,
                amount = 8000.0,
                category = "Transportasi",
                note = "KRL commuter line",
                timestamp = now - 96400000
            )
        )
        repository.addTransaction(
            CardTransaction(
                cardId = brizziCard.id,
                amount = 12000.0,
                category = "Tol & Parkir",
                note = "Parkiran Mal Gandaria",
                timestamp = now - 106400000
            )
        )

        _selectedCardId.value = flazzCard.id
    }

    fun selectCard(cardId: String) {
        _selectedCardId.value = cardId
    }

    fun handlePhysicalNfcTag(uidHex: String, cardType: String = "Lainnya") {
        viewModelScope.launch {
            _scanState.value = NfcScanState.Scanning
            val existingCard = repository.getCardById(uidHex)
            if (existingCard != null) {
                // Update tapping timestamp
                val updatedCard = existingCard.copy(lastTappedTime = System.currentTimeMillis())
                repository.insertOrUpdateCard(updatedCard)
                _selectedCardId.value = updatedCard.id
                _scanState.value = NfcScanState.Success(updatedCard, isNew = false)
            } else {
                // Automatically register a new NFC Tag
                val typeName = when {
                    uidHex.startsWith("04") -> "Mifare Ultralight" // Common tag signature
                    else -> cardType
                }
                val colorHex = when (cardType) {
                    "BCA Flazz" -> "#1e3c72,#2a5298"
                    "Mandiri e-Money" -> "#0F2027,#203A43,#2C5364"
                    "BRI Brizzi" -> "#134E5E,#71B280"
                    "BNI TapCash" -> "#f12711,#f5af19" // Warm gold orange
                    else -> "#3a1c71,#d76d77,#ffaf7b" // Tricolor sunset for custom tags
                }
                val nameGuess = when (cardType) {
                    "Lainnya" -> "Kartu NFC (${uidHex.takeLast(4)})"
                    else -> "$cardType (${uidHex.takeLast(4)})"
                }
                val newCard = NfcCard(
                    id = uidHex,
                    name = nameGuess,
                    type = cardType,
                    balance = 100000.0, // Pre-seed new taps with a demo balance of 100.000 so it can be immediately spent
                    lastTappedTime = System.currentTimeMillis(),
                    cardColorHex = colorHex
                )
                repository.insertOrUpdateCard(newCard)
                _selectedCardId.value = newCard.id
                _scanState.value = NfcScanState.Success(newCard, isNew = true)
            }
        }
    }

    fun handleSimulatedTap(type: String, balance: Double, nameInput: String = "") {
        viewModelScope.launch {
            _scanState.value = NfcScanState.Scanning
            kotlinx.coroutines.delay(600) // Realistic scanning feedback delay

            val randomHex = (10000000..99999999).random().toString(16).uppercase()
            val uid = "SIM_${type.replace(" ", "_").uppercase()}_$randomHex"
            
            val color = when (type) {
                "BCA Flazz" -> "#1e3c72,#2a5298"
                "Mandiri e-Money" -> "#0F2027,#203A43,#2C5364"
                "BRI Brizzi" -> "#134E5E,#71B280"
                "BNI TapCash" -> "#f12711,#f5af19"
                else -> "#3f2b96,#a8c0ff"
            }
            
            val displayName = nameInput.ifEmpty { "$type (${uid.takeLast(4)})" }
            val card = NfcCard(
                id = uid,
                name = displayName,
                type = type,
                balance = balance,
                lastTappedTime = System.currentTimeMillis(),
                cardColorHex = color
            )
            repository.insertOrUpdateCard(card)
            _selectedCardId.value = card.id
            _scanState.value = NfcScanState.Success(card, isNew = true)
        }
    }

    fun clearScanState() {
        _scanState.value = NfcScanState.Idle
    }

    fun addManualTransaction(cardId: String, amount: Double, category: String, note: String) {
        viewModelScope.launch {
            val transaction = CardTransaction(
                cardId = cardId,
                amount = amount,
                category = category,
                note = note.ifEmpty { "Pengeluaran tanpa catatan" },
                timestamp = System.currentTimeMillis()
            )
            repository.addTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: CardTransaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun updateCardName(cardId: String, newName: String) {
        viewModelScope.launch {
            val card = repository.getCardById(cardId)
            if (card != null && newName.isNotBlank()) {
                repository.insertOrUpdateCard(card.copy(name = newName))
            }
        }
    }

    fun updateCardBalanceManual(cardId: String, newBalance: Double) {
        viewModelScope.launch {
            repository.updateBalance(cardId, newBalance)
        }
    }

    fun deleteCard(card: NfcCard) {
        viewModelScope.launch {
            repository.deleteCard(card)
            val updatedCards = cards.value.filter { it.id != card.id }
            _selectedCardId.value = updatedCards.firstOrNull()?.id
        }
    }
}
