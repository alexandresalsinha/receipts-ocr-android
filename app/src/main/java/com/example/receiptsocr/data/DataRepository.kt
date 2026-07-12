package com.example.receiptsocr.data

import com.example.receiptsocr.data.db.ReceiptDao
import com.example.receiptsocr.data.model.ReceiptEntity
import kotlinx.coroutines.flow.Flow

interface DataRepository {
    val receipts: Flow<List<ReceiptEntity>>
    suspend fun getReceiptById(id: String): ReceiptEntity?
    suspend fun insertReceipt(receipt: ReceiptEntity)
    suspend fun deleteReceipt(receipt: ReceiptEntity)
}

class DefaultDataRepository(private val receiptDao: ReceiptDao) : DataRepository {
    override val receipts: Flow<List<ReceiptEntity>> = receiptDao.getAllReceipts()

    override suspend fun getReceiptById(id: String): ReceiptEntity? {
        return receiptDao.getReceiptById(id)
    }

    override suspend fun insertReceipt(receipt: ReceiptEntity) {
        receiptDao.insertReceipt(receipt)
    }

    override suspend fun deleteReceipt(receipt: ReceiptEntity) {
        receiptDao.deleteReceipt(receipt)
    }
}
