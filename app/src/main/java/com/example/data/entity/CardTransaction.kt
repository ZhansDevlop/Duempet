package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_transactions",
    foreignKeys = [
        ForeignKey(
            entity = NfcCard::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CardTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cardId: String,
    val amount: Double,
    val category: String, // e.g. "Tol & Parkir", "Transportasi", "Makanan", "Supermarket", "Lainnya"
    val note: String,
    val timestamp: Long
)
