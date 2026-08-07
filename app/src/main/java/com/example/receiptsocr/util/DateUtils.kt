package com.example.receiptsocr.util

/**
 * Normalizes a raw receipt date string to zero-padded dd/MM/yyyy so that grouping,
 * sorting and equality checks are consistent regardless of how the OCR returned it.
 *
 * Handles "d/M/yyyy", "dd/MM/yyyy" and "yyyy-MM-dd". Returns the trimmed original if it
 * doesn't match a known shape, or null if blank.
 */
fun normalizeReceiptDate(raw: String?): String? {
    val value = raw?.trim()
    if (value.isNullOrEmpty()) return null

    // yyyy-MM-dd
    value.split("-").takeIf { it.size == 3 && it[0].length == 4 }?.let { (y, m, d) ->
        if (allDigits(y, m, d)) return "${pad(d)}/${pad(m)}/$y"
    }

    // d/M/yyyy or dd/MM/yyyy
    value.split("/").takeIf { it.size == 3 }?.let { (d, m, y) ->
        if (y.length == 4 && allDigits(d, m, y)) return "${pad(d)}/${pad(m)}/$y"
    }

    return value
}

private fun pad(part: String): String = part.trim().padStart(2, '0')

private fun allDigits(vararg parts: String): Boolean =
    parts.all { it.trim().isNotEmpty() && it.trim().all(Char::isDigit) }
