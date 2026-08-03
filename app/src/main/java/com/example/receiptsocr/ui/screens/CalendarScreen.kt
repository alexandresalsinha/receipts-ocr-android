package com.example.receiptsocr.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.receiptsocr.data.model.ReceiptEntity
import com.example.receiptsocr.ui.viewmodel.ReceiptViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Highlight color used for the most expensive day of the displayed month.
private val ExpensiveDayColor = Color(0xFFE53935)

/**
 * Parses a stored receipt date (expected `dd/MM/yyyy`, but tolerant of non zero-padded
 * values like `3/8/2026`) into (day, month, year). Returns null for missing/malformed dates.
 */
private fun parseReceiptDate(dateStr: String?): Triple<Int, Int, Int>? {
    if (dateStr.isNullOrBlank()) return null
    val parts = dateStr.split("/")
    if (parts.size != 3) return null
    val day = parts[0].trim().toIntOrNull() ?: return null
    val month = parts[1].trim().toIntOrNull() ?: return null
    val year = parts[2].trim().toIntOrNull() ?: return null
    return Triple(day, month, year)
}

/**
 * Screen that lets the user pick a day from an inline month calendar and see all the
 * receipts recorded for that day along with their combined total.
 *
 * The calendar marks every day that has receipts with a dot, and paints the single most
 * expensive day of the displayed month in red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: ReceiptViewModel,
    onBackClick: () -> Unit,
    onReceiptClick: (ReceiptEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val receipts by viewModel.allReceipts.collectAsState()
    val formatter = DecimalFormat("€#,##0.00")

    val now = remember { Calendar.getInstance() }
    // Currently displayed month (1-based month to keep it aligned with the stored date format).
    var displayedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    // Currently selected day.
    var selYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var selDay by remember { mutableIntStateOf(now.get(Calendar.DAY_OF_MONTH)) }

    // Per-day spending totals for the displayed month, keyed by day-of-month.
    val monthTotals = remember(receipts, displayedYear, displayedMonth) {
        val map = mutableMapOf<Int, Double>()
        receipts.forEach { receipt ->
            val parsed = parseReceiptDate(receipt.date) ?: return@forEach
            val (d, m, y) = parsed
            if (y == displayedYear && m == displayedMonth) {
                map[d] = (map[d] ?: 0.0) + (receipt.totalAmount ?: 0.0)
            }
        }
        map
    }
    // The most expensive day of the displayed month (only among days with actual spending).
    val mostExpensiveDay = remember(monthTotals) {
        monthTotals.filterValues { it > 0.0 }.maxByOrNull { it.value }?.key
    }

    val monthLabel = remember(displayedYear, displayedMonth) {
        val c = Calendar.getInstance().apply { clear(); set(displayedYear, displayedMonth - 1, 1) }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time)
    }

    // Calendar cells laid out Monday-first, chunked into weeks. null = padding cell.
    val weeks = remember(displayedYear, displayedMonth) {
        val c = Calendar.getInstance().apply { clear(); set(displayedYear, displayedMonth - 1, 1) }
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = c.get(Calendar.DAY_OF_WEEK) // 1=Sun .. 7=Sat
        val leading = (firstDow + 5) % 7 // Monday -> 0, Sunday -> 6
        val cells = ArrayList<Int?>()
        repeat(leading) { cells.add(null) }
        for (d in 1..daysInMonth) cells.add(d)
        while (cells.size % 7 != 0) cells.add(null)
        cells.chunked(7)
    }

    val selectedLabel = String.format(Locale.getDefault(), "%02d/%02d/%04d", selDay, selMonth, selYear)

    val dayReceipts = remember(receipts, selDay, selMonth, selYear) {
        receipts
            .filter {
                val parsed = parseReceiptDate(it.date)
                parsed != null && parsed.first == selDay && parsed.second == selMonth && parsed.third == selYear
            }
            .sortedByDescending { it.timestamp }
    }
    val dayTotal = dayReceipts.sumOf { it.totalAmount ?: 0.0 }

    // Only mark the selection on the grid when the selected day belongs to the displayed month.
    val selectedInDisplayedMonth = if (selMonth == displayedMonth && selYear == displayedYear) selDay else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipts by Day") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
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
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Inline month calendar
            item {
                MonthCalendar(
                    monthLabel = monthLabel,
                    weeks = weeks,
                    daysWithReceipts = monthTotals.keys,
                    mostExpensiveDay = mostExpensiveDay,
                    selectedDay = selectedInDisplayedMonth,
                    onPreviousMonth = {
                        if (displayedMonth == 1) {
                            displayedMonth = 12; displayedYear -= 1
                        } else displayedMonth -= 1
                    },
                    onNextMonth = {
                        if (displayedMonth == 12) {
                            displayedMonth = 1; displayedYear += 1
                        } else displayedMonth += 1
                    },
                    onSelectDay = { day ->
                        selDay = day; selMonth = displayedMonth; selYear = displayedYear
                    }
                )
            }

            // Day total summary card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                                text = selectedLabel,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatter.format(dayTotal),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${dayReceipts.size} ${if (dayReceipts.size == 1) "receipt" else "receipts"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Receipts for the selected day
            if (dayReceipts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
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
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No receipts on this day",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pick another day from the calendar above",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(dayReceipts, key = { it.id }) { receipt ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ReceiptItemRow(
                            receipt = receipt,
                            onClick = { onReceiptClick(receipt) },
                            onDelete = { viewModel.deleteReceipt(receipt) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    monthLabel: String,
    weeks: List<List<Int?>>,
    daysWithReceipts: Set<Int>,
    mostExpensiveDay: Int?,
    selectedDay: Int?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            // Month navigation header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = monthLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday labels (Monday-first)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Day grid
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            hasReceipts = day != null && day in daysWithReceipts,
                            isSelected = day != null && day == selectedDay,
                            isMostExpensive = day != null && day == mostExpensiveDay,
                            modifier = Modifier.weight(1f),
                            onClick = { if (day != null) onSelectDay(day) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int?,
    hasReceipts: Boolean,
    isSelected: Boolean,
    isMostExpensive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .then(if (day != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (day == null) return@Box

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected || isMostExpensive) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isMostExpensive -> ExpensiveDayColor
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            // Receipt indicator dot
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !hasReceipts -> Color.Transparent
                            isMostExpensive -> ExpensiveDayColor
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}
