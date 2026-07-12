package com.example.receiptsocr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun testCoffeeShopReceipt() {
        val ocrText = """
            STARBUCKS COFFEE #1234
            123 MAIN STREET
            NEW YORK, NY 10001
            TEL: 555-123-4567
            
            1x CAFFE LATTE     $4.50
            1x BLUEBERRY MUFFIN $3.75
            
            SUBTOTAL:          $8.25
            TAX (8.875%):       $0.73
            TOTAL:             $8.98
            
            PAID: Visa ************1111
            CHANGE:            $0.00
            
            DATE: 2026-07-08 14:35:21
            THANK YOU FOR VISITING!
        """.trimIndent()

        val receipt = ReceiptParser.parse(ocrText)

        assertEquals("STARBUCKS COFFEE 1234", receipt.merchantName)
        assertEquals("08/07/2026", receipt.date)
        assertEquals(8.98, receipt.totalAmount ?: 0.0, 0.001)
        assertEquals("Food & Dining", receipt.category)
    }

    @Test
    fun testGroceryReceiptWithCommaDecimals() {
        val ocrText = """
            ALDI SUPERMARKET
            STORE MANAGER: JOHN DOE
            
            BREAD               1,89
            ORGANIC MILK        3,49
            EGGS 12PK           2,99
            
            TOTAL DUE         8,37
            CASH PAID          10,00
            CHANGE             1,63
            
            12/05/2025  09:12
        """.trimIndent()

        val receipt = ReceiptParser.parse(ocrText)

        assertEquals("ALDI SUPERMARKET", receipt.merchantName)
        assertEquals("12/05/2025", receipt.date)
        assertEquals(8.37, receipt.totalAmount ?: 0.0, 0.001)
        assertEquals("Groceries", receipt.category)
    }
}
