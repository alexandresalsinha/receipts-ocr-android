package com.example.receiptsocr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val merchantName: String?,
    val date: String?,
    val totalAmount: Double?,
    val currency: String = "$",
    val category: String,
    val itemsJson: String, // JSON serialized list of ReceiptItem
    val rawText: String,
    val imagePath: String?,
    val timestamp: Long = System.currentTimeMillis()
)
