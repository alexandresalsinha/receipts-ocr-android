package com.example.receiptsocr.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptItem(
    val name: String,
    val price: Double
)
