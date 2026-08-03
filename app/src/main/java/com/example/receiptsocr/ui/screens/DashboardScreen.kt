package com.example.receiptsocr.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.receiptsocr.data.model.ReceiptEntity
import com.example.receiptsocr.ui.viewmodel.ReceiptViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

// Category configurations
val CATEGORY_COLORS = mapOf(
    "Groceries" to Color(0xFF4CAF50),
    "Food & Dining" to Color(0xFFFF5722),
    "Travel" to Color(0xFF2196F3),
    "Shopping" to Color(0xFF9C27B0),
    "Utilities" to Color(0xFF009688),
    "Miscellaneous" to Color(0xFF757575)
)

val CATEGORIES = listOf("Groceries", "Food & Dining", "Travel", "Shopping", "Utilities", "Miscellaneous")

fun getSortableDate(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    val parts = dateStr.split("/")
    return if (parts.size == 3) {
        // DD/MM/YYYY -> YYYYMMDD
        "${parts[2]}${parts[1]}${parts[0]}"
    } else {
        // Fallback for YYYY-MM-DD or other formats
        dateStr.replace("-", "")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: ReceiptViewModel,
    onScanClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onReceiptClick: (ReceiptEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val receipts by viewModel.receipts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    
    val totalSpent = receipts.sumOf { it.totalAmount ?: 0.0 }
    val formatter = DecimalFormat("€#,##0.00")

    // Today's total (dates are stored as DD/MM/YYYY)
    val today = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date()) }
    val todayTotal = receipts
        .filter { it.date == today }
        .sumOf { it.totalAmount ?: 0.0 }
    
    // Group receipts by date, sorted by date descending
    val receiptsByDay = remember(receipts) {
        receipts.sortedByDescending { getSortableDate(it.date) }
            .groupBy { it.date ?: "Unknown Date" }
    }

    // Category Breakdown for Chart
    val categoryBreakdown = remember(receipts) {
        val breakdown = mutableMapOf<String, Double>()
        receipts.forEach { receipt ->
            val amt = receipt.totalAmount ?: 0.0
            breakdown[receipt.category] = (breakdown[receipt.category] ?: 0.0) + amt
        }
        breakdown
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScanClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Scan Receipt", modifier = Modifier.size(32.dp))
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            // Header Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Receipts Tracker",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onCalendarClick) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Browse receipts by day",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Spent Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Total Spending",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatter.format(totalSpent),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Today's Spending",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatter.format(todayTotal),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${receipts.size} Receipts scanned",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    }

                    // Mini Donut Chart
                    if (totalSpent > 0) {
                        DonutChart(
                            breakdown = categoryBreakdown,
                            total = totalSpent,
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search merchant, category, items...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )

            // Category filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("All") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                items(CATEGORIES) { category ->
                    val isSelected = selectedCategory == category
                    val categoryColor = CATEGORY_COLORS[category] ?: Color.Gray
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setCategoryFilter(if (isSelected) null else category) },
                        label = { Text(category) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor.copy(alpha = 0.2f),
                            selectedLabelColor = categoryColor,
                            selectedLeadingIconColor = categoryColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Receipts List
            if (receipts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedCategory != null) "No matching receipts found" else "No receipts scanned yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedCategory != null) "Try adjusting your search criteria" else "Tap the '+' button to scan your first receipt",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    receiptsByDay.forEach { (date, dayReceipts) ->
                        stickyHeader {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            ) {
                                Text(
                                    text = date,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(dayReceipts, key = { it.id }) { receipt ->
                            ReceiptItemRow(
                                receipt = receipt,
                                onClick = { onReceiptClick(receipt) },
                                onDelete = { viewModel.deleteReceipt(receipt) }
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Avoid covering by FAB
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChart(
    breakdown: Map<String, Double>,
    total: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 10.dp.toPx()
        val chartSize = size.minDimension - strokeWidth
        val topLeftX = (size.width - chartSize) / 2
        val topLeftY = (size.height - chartSize) / 2
        
        var startAngle = -90f
        
        if (total > 0) {
            breakdown.forEach { (category, amount) ->
                val sweepAngle = (amount / total * 360f).toFloat()
                val color = CATEGORY_COLORS[category] ?: Color.Gray
                
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                    size = Size(chartSize, chartSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }
        } else {
            drawArc(
                color = Color.LightGray,
                startAngle = startAngle,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                size = Size(chartSize, chartSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun ReceiptItemRow(
    receipt: ReceiptEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = CATEGORY_COLORS[receipt.category] ?: Color.Gray
    val formatter = DecimalFormat("€#,##0.00")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category color circle
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = receipt.merchantName ?: "Unknown Merchant",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = receipt.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryColor
                        )
                        if (!receipt.date.isNullOrEmpty()) {
                            Text(
                                text = " • ${receipt.date}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = receipt.totalAmount?.let { formatter.format(it) } ?: "€0.00",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Receipt",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
