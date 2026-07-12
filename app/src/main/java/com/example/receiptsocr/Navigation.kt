package com.example.receiptsocr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.receiptsocr.data.DefaultDataRepository
import com.example.receiptsocr.data.db.ReceiptDatabase
import com.example.receiptsocr.ui.screens.CameraScreen
import com.example.receiptsocr.ui.screens.DashboardScreen
import com.example.receiptsocr.ui.screens.DetailScreen
import com.example.receiptsocr.ui.viewmodel.ReceiptViewModel
import com.example.receiptsocr.ui.viewmodel.ReceiptViewModelFactory

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    
    // Initialize Database, Repository and ViewModel
    val database = remember { ReceiptDatabase.getDatabase(context.applicationContext) }
    val repository = remember { DefaultDataRepository(database.receiptDao()) }
    val receiptViewModel: ReceiptViewModel = viewModel(
        factory = ReceiptViewModelFactory(repository)
    )

    // Set Dashboard as root screen
    val backStack = rememberNavBackStack(Dashboard)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Dashboard> {
                DashboardScreen(
                    viewModel = receiptViewModel,
                    onScanClick = { backStack.add(Camera) },
                    onReceiptClick = { receipt ->
                        receiptViewModel.setActiveReceipt(receipt)
                        backStack.add(Detail)
                    }
                )
            }
            entry<Camera> {
                CameraScreen(
                    viewModel = receiptViewModel,
                    onBackClick = { backStack.removeLastOrNull() },
                    onReceiptParsed = {
                        // Switch from Camera to Detail (remove Camera from stack to avoid returning to scanner)
                        backStack.removeLastOrNull()
                        backStack.add(Detail)
                    }
                )
            }
            entry<Detail> {
                DetailScreen(
                    viewModel = receiptViewModel,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
