package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_cards")
data class NfcCard(
    @PrimaryKey val id: String, // Hex UID of the card or generated mock ID
    val name: String,
    val type: String,           // e.g. "BCA Flazz", "Mandiri e-Money", "BRI Brizzi", "BNI TapCash", "Custom"
    val balance: Double,
    val lastTappedTime: Long,
    val cardColorHex: String    // Rich UI representation - holds color codes or style strings
)
