package com.example.receiptsocr.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.receiptsocr.data.model.ReceiptEntity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.receiptsocr.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    val receiptsByDay = FAKE_RECEIPTS
        .sortedByDescending { getSortableDate(it.date) }
        .groupBy { it.date ?: "Unknown Date" }
    composeTestRule.setContent {
      MainScreen(
          receiptsByDay = receiptsByDay,
          onAddReceipt = {},
          onDeleteReceipt = {}
      )
    }
  }

  @Test
  fun receipts_exist() {
    FAKE_RECEIPTS.forEach {
      composeTestRule.onNodeWithText(it.merchantName ?: "Unknown Merchant").assertExists()
    }
  }
}

fun getSortableDate(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    val parts = dateStr.split("/")
    return if (parts.size == 3) {
        "${parts[2]}${parts[1]}${parts[0]}"
    } else {
        dateStr.replace("-", "")
    }
}

private val FAKE_RECEIPTS = listOf(
    ReceiptEntity(
        id = "1",
        merchantName = "Starbucks",
        date = "01/10/2023",
        totalAmount = 5.50,
        category = "Food",
        itemsJson = "[]",
        rawText = "Starbucks Coffee",
        imagePath = null
    ),
    ReceiptEntity(
        id = "2",
        merchantName = "Walmart",
        date = "01/10/2023",
        totalAmount = 42.15,
        category = "Groceries",
        itemsJson = "[]",
        rawText = "Walmart Supercenter",
        imagePath = null
    ),
    ReceiptEntity(
        id = "3",
        merchantName = "Shell",
        date = "02/10/2023",
        totalAmount = 50.00,
        category = "Fuel",
        itemsJson = "[]",
        rawText = "Shell Gas Station",
        imagePath = null
    )
)
