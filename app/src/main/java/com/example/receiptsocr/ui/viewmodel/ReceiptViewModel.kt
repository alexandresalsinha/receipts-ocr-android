package com.example.receiptsocr.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.receiptsocr.data.DataRepository
import com.example.receiptsocr.data.model.ReceiptEntity
import com.example.receiptsocr.data.model.ReceiptItem
import com.example.receiptsocr.domain.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ReceiptViewModel(private val repository: DataRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<String?>(null)
    
    val isProcessing = MutableStateFlow(false)
    val ocrError = MutableStateFlow<String?>(null)
    
    // The currently active receipt being edited / reviewed
    val activeReceipt = MutableStateFlow<ReceiptEntity?>(null)

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
                // Copy image to app local storage to persist it
                val persistedImagePath = copyImageToInternalStorage(context, uri)
                
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        if (text.trim().isEmpty()) {
                            ocrError.value = "No text could be recognized in the image. Please try again with a clearer picture."
                            isProcessing.value = false
                        } else {
                            val parsed = ReceiptParser.parse(text, persistedImagePath)
                            activeReceipt.value = parsed
                            isProcessing.value = false
                        }
                    }
                    .addOnFailureListener { e ->
                        ocrError.value = "OCR Failed: ${e.localizedMessage ?: "Unknown error"}"
                        isProcessing.value = false
                    }
            } catch (e: Exception) {
                ocrError.value = "Failed to load image: ${e.localizedMessage ?: "Unknown error"}"
                isProcessing.value = false
            }
        }
    }

    private fun copyImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "receipt_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
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
