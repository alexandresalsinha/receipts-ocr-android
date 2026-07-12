package com.example.receiptsocr.domain

import com.example.receiptsocr.data.model.ReceiptEntity
import com.example.receiptsocr.data.model.ReceiptItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

object ReceiptParser {

    private val DATE_PATTERNS = listOf(
        Pattern.compile("\\b(\\d{4})[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\\d|3[01])\\b"), // YYYY-MM-DD
        Pattern.compile("\\b(0[1-9]|[12]\\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](19|20)?(\\d{2})\\b"), // DD-MM-YY/YYYY
        Pattern.compile("\\b(0[1-9]|1[0-2])[-/.](0[1-9]|[12]\\d|3[01])[-/.](19|20)?(\\d{2})\\b")  // MM-DD-YY/YYYY
    )

    private val PRICE_PATTERN = Pattern.compile("[-+]?\\d+([.,])\\d{2}\\b")

    private val TOTAL_KEYWORDS = listOf(
        "total", "grand total", "total due", "amount due", "amt due", 
        "total amount", "netto", "sum", "balance", "pay", "paid", 
        "visa", "mastercard", "amex", "cash", "debit", "card"
    )

    private val IGNORE_MERCHANT_KEYWORDS = listOf(
        "welcome", "receipt", "tax invoice", "invoice", "sale", "duplicate",
        "store", "customer", "merchant", "terminal", "trans", "station", "shop",
        "tel", "phone", "date", "time", "cashier", "order", "item", "qty", "price"
    )

    fun parse(rawText: String, imagePath: String? = null): ReceiptEntity {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val merchant = extractMerchant(lines)
        val date = extractDate(rawText)
        val prices = extractAllPrices(rawText)
        val total = extractTotal(lines, prices)
        val items = extractLineItems(lines)
        val category = determineCategory(merchant, items)

        val itemsJson = Json.encodeToString(items)

        return ReceiptEntity(
            id = UUID.randomUUID().toString(),
            merchantName = merchant,
            date = date,
            totalAmount = total,
            category = category,
            itemsJson = itemsJson,
            rawText = rawText,
            imagePath = imagePath,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun extractMerchant(lines: List<String>): String {
        // Look at first 6 lines
        for (i in 0 until minOf(lines.size, 6)) {
            val line = lines[i]
            val lowerLine = line.lowercase(Locale.ROOT)
            
            // Skip lines that look like numbers/dates/urls
            if (line.matches(Regex("^[\\d\\W]+$")) || 
                lowerLine.contains("http") || 
                lowerLine.contains(".com") || 
                lowerLine.contains("tel:") ||
                lowerLine.contains("phone") ||
                lowerLine.contains("street") ||
                lowerLine.contains("road") ||
                lowerLine.contains("ave")
            ) {
                continue
            }

            // Skip common receipt header text
            val shouldIgnore = IGNORE_MERCHANT_KEYWORDS.any { keyword -> 
                lowerLine.contains(keyword) 
            }
            if (shouldIgnore) {
                continue
            }

            // Clean the merchant name (remove extra special chars)
            val cleaned = line.replace(Regex("[#*:\\-\\[\\](){}]"), "").trim()
            if (cleaned.length > 2) {
                return cleaned
            }
        }
        return "Unknown Merchant"
    }

    private fun extractDate(rawText: String): String {
        for (pattern in DATE_PATTERNS) {
            val matcher = pattern.matcher(rawText)
            if (matcher.find()) {
                return matcher.group(0) ?: ""
            }
        }
        
        // Return current date format placeholder or empty if not found
        return ""
    }

    private fun extractAllPrices(rawText: String): List<Double> {
        val prices = mutableListOf<Double>()
        val matcher = PRICE_PATTERN.matcher(rawText)
        while (matcher.find()) {
            val match = matcher.group(0)
            try {
                // Normalize price decimal separator to '.'
                val normalized = match.replace(',', '.')
                val price = normalized.toDouble()
                prices.add(price)
            } catch (e: NumberFormatException) {
                // Ignore parsing errors
            }
        }
        return prices
    }

    private fun extractTotal(lines: List<String>, allPrices: List<Double>): Double? {
        // 1. Keyword search heuristic
        for (line in lines) {
            val lowerLine = line.lowercase(Locale.ROOT)
            val isTotalLine = TOTAL_KEYWORDS.any { keyword ->
                lowerLine.contains(keyword) && !lowerLine.contains("subtotal") && !lowerLine.contains("tax") && !lowerLine.contains("vat")
            }
            
            if (isTotalLine) {
                // Find decimal prices in this line
                val matcher = PRICE_PATTERN.matcher(line)
                var lastPrice: Double? = null
                while (matcher.find()) {
                    val pStr = matcher.group(0).replace(',', '.')
                    pStr.toDoubleOrNull()?.let { lastPrice = it }
                }
                if (lastPrice != null) {
                    return lastPrice
                }
            }
        }

        // 2. Fallback: Choose the largest value in the receipt
        // Filter out very large numbers that could be card numbers, phone numbers or zip codes (e.g. > 2000.00)
        // Filter out negative values
        val plausiblePrices = allPrices.filter { it in 0.01..2500.00 }
        if (plausiblePrices.isNotEmpty()) {
            return plausiblePrices.maxOrNull()
        }

        return null
    }

    private fun extractLineItems(lines: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        // Match descriptions followed by prices, like: Coffee 3.50 or Milk $1.99 or 2x Bread 4.00
        val lineItemRegex = Regex("^(.*?)\\s+[\$€£]?\\s*(\\d+([.,])\\d{2})\\s*$")

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            
            // Skip lines with total/subtotal/tax keywords
            val isTotalOrHeader = TOTAL_KEYWORDS.any { lower.contains(it) } || 
                                  lower.contains("subtotal") || 
                                  lower.contains("tax") || 
                                  lower.contains("vat") ||
                                  lower.contains("change") ||
                                  lower.contains("cashier")
            if (isTotalOrHeader) continue

            val matchResult = lineItemRegex.find(line)
            if (matchResult != null) {
                val desc = matchResult.groups[1]?.value?.trim() ?: ""
                val priceStr = matchResult.groups[2]?.value?.replace(',', '.') ?: ""
                val price = priceStr.toDoubleOrNull() ?: 0.0

                // Ignore if description is too short, look like a date or has only digits/special chars
                if (desc.length > 2 && !desc.matches(Regex("^[\\d\\W]+$")) && !desc.lowercase(Locale.ROOT).contains("welcome")) {
                    items.add(ReceiptItem(name = desc, price = price))
                }
            }
        }
        return items
    }

    private fun determineCategory(merchant: String, items: List<ReceiptItem>): String {
        val merchantLower = merchant.lowercase(Locale.ROOT)
        
        // Check merchant names
        if (containsAny(merchantLower, "walmart", "target", "costco", "aldi", "kroger", "tesco", "supermarket", "grocery", "whole foods", "lidl", "carrefour")) {
            return "Groceries"
        }
        if (containsAny(merchantLower, "mcdonald", "starbucks", "subway", "burger king", "cafe", "restaurant", "pizza", "dunkin", "tacobell", "kfc", "bistro", "pub", "bar", "coffee")) {
            return "Food & Dining"
        }
        if (containsAny(merchantLower, "uber", "lyft", "taxi", "shell", "bp", "exxon", "gas", "chevron", "airline", "train", "metro", "subway transport", "transit", "fuel")) {
            return "Travel"
        }
        if (containsAny(merchantLower, "amazon", "apple", "best buy", "h&m", "zara", "nike", "store", "mall", "decathlon", "ikea", "nordstrom", "macys")) {
            return "Shopping"
        }
        if (containsAny(merchantLower, "electric", "power", "water", "gas utility", "internet", "comcast", "verizon", "phone", "at&t", "t-mobile")) {
            return "Utilities"
        }

        // Check item names
        val allItemNames = items.joinToString(" ") { it.name.lowercase(Locale.ROOT) }
        if (containsAny(allItemNames, "coffee", "latte", "burger", "pizza", "noodle", "sandwich", "drink", "soda", "beer", "wine", "fry", "salad")) {
            return "Food & Dining"
        }
        if (containsAny(allItemNames, "milk", "bread", "egg", "apple", "banana", "cheese", "vegetable", "fruit", "grocery", "cereal", "yogurt")) {
            return "Groceries"
        }
        if (containsAny(allItemNames, "shirt", "pants", "shoes", "jacket", "dress", "socks", "book", "toy", "charger", "cable")) {
            return "Shopping"
        }
        if (containsAny(allItemNames, "gasoline", "fuel", "diesel", "ticket", "fare", "toll")) {
            return "Travel"
        }

        return "Miscellaneous"
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}
