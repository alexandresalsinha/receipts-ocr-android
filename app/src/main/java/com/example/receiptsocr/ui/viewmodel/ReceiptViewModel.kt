package com.example.receiptsocr.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.receiptsocr.data.DataRepository
import com.example.receiptsocr.data.model.ReceiptEntity
import com.example.receiptsocr.data.remote.GeminiClient
import com.example.receiptsocr.util.normalizeReceiptDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReceiptViewModel(private val repository: DataRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<String?>(null)
    
    val isProcessing = MutableStateFlow(false)
    val ocrError = MutableStateFlow<String?>(null)
    
    // The currently active receipt being edited / reviewed
    val activeReceipt = MutableStateFlow<ReceiptEntity?>(null)

    // Unfiltered list of all receipts (used by the calendar day view)
    val allReceipts: StateFlow<List<ReceiptEntity>> = repository.receipts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined list of receipts filtered by search query and category
    val receipts: StateFlow<List<ReceiptEntity>> = combine(
        repository.receipts,
        searchQuery,
        selectedCategoryFilter
    ) { list, query, category ->
        list.filter { receipt ->
            val matchesQuery = query.isEmpty() || 
                    (receipt.merchantName?.contains(query, ignoreCase = true) == true) ||
                    (receipt.category.contains(query, ignoreCase = true)) ||
                    (receipt.rawText.contains(query, ignoreCase = true))
            
            val matchesCategory = category == null || receipt.category == category
            
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        selectedCategoryFilter.value = category
    }

    fun clearOcrError() {
        ocrError.value = null
    }

    fun setActiveReceipt(receipt: ReceiptEntity?) {
        activeReceipt.value = receipt
    }

    fun updateActiveReceiptMerchant(name: String) {
        activeReceipt.value = activeReceipt.value?.copy(merchantName = name)
    }

    fun updateActiveReceiptDate(date: String) {
        activeReceipt.value = activeReceipt.value?.copy(date = date)
    }

    fun updateActiveReceiptTotal(total: Double?) {
        activeReceipt.value = activeReceipt.value?.copy(totalAmount = total)
    }

    fun updateActiveReceiptCategory(category: String) {
        activeReceipt.value = activeReceipt.value?.copy(category = category)
    }

    fun saveActiveReceipt() {
        activeReceipt.value?.let { receipt ->
            viewModelScope.launch {
                repository.insertReceipt(receipt)
                activeReceipt.value = null
            }
        }
    }

    fun deleteReceipt(receipt: ReceiptEntity) {
        viewModelScope.launch {
            // Delete image file if exists
            receipt.imagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            repository.deleteReceipt(receipt)
        }
    }

    fun processImageUri(context: Context, uri: Uri) {
        isProcessing.value = true
        ocrError.value = null

        viewModelScope.launch {
            try {
                // Read the image bytes and copy it to app local storage so it can be displayed later.
                val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (imageBytes == null || imageBytes.isEmpty()) {
                    ocrError.value = "Failed to load the selected image. Please try again."
                    isProcessing.value = false
                    return@launch
                }

                val persistedImagePath = copyImageToInternalStorage(context, imageBytes)

                // Send the photo to the Gemini vision model and ask it to read the receipt.
                val extraction = GeminiClient.extractReceipt(imageBytes)

                activeReceipt.value = ReceiptEntity(
                    id = UUID.randomUUID().toString(),
                    merchantName = extraction.merchantName ?: "Unknown Merchant",
                    date = normalizeReceiptDate(extraction.date) ?: todayFormatted(),
                    totalAmount = extraction.totalAmount,
                    category = extraction.category ?: "Miscellaneous",
                    itemsJson = Json.encodeToString(extraction.items),
                    rawText = extraction.rawResponse,
                    imagePath = persistedImagePath,
                    timestamp = System.currentTimeMillis()
                )
                isProcessing.value = false
            } catch (e: Exception) {
                ocrError.value = "Could not read the receipt: ${e.localizedMessage ?: "Unknown error"}"
                isProcessing.value = false
            }
        }
    }

    private fun todayFormatted(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
    }

    private fun copyImageToInternalStorage(context: Context, imageBytes: ByteArray): String? {
        return try {
            val fileName = "receipt_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(imageBytes)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class ReceiptViewModelFactory(private val repository: DataRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReceiptViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReceiptViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
