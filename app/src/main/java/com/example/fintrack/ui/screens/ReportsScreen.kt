package com.example.fintrack.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.data.model.HistoryDomain
import com.example.fintrack.data.model.TransactionType
import com.example.fintrack.data.model.UnifiedHistoryItem
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Indigo600
import com.example.fintrack.ui.theme.Rose600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: KhataViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Unified History, 1: Analytics & Reports

    val historyList by viewModel.unifiedHistoryList.collectAsState()
    val activeDomainFilter by viewModel.historyDomainFilter.collectAsState()

    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val borrowers by viewModel.customers.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val udharCustomers by viewModel.udharCustomers.collectAsState()
    val udharEntries by viewModel.udharEntries.collectAsState()

    val totalIncome = remember(transactions) {
        transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val netCashflow = totalIncome - totalExpense

    val expenseCategories = remember(transactions) {
        transactions.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
    }

    val totalUdharGiven = remember(udharCustomers, udharEntries) {
        udharCustomers.sumOf { it.calculateTotalGave(udharEntries) }
    }
    val totalUdharReceived = remember(udharCustomers, udharEntries) {
        udharCustomers.sumOf { it.calculateTotalGot(udharEntries) }
    }
    val totalUdharPending = (totalUdharGiven - totalUdharReceived).coerceAtLeast(0.0)

    val totalLentPrincipal = remember(borrowers) { borrowers.sumOf { it.remainingPrincipal } }
    val totalInterestAccrued = remember(borrowers) { borrowers.sumOf { it.outstandingInterest } }
    val totalRepaymentsCollected = remember(payments) { payments.sumOf { it.amount } }

    var showExportDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.ENGLISH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Reports & History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("btn_export_reports")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV", tint = Emerald600)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.testTag("reports_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Top Tab
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unified History (${historyList.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_unified_history")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analytics & Audit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_analytics_reports")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // TAB 0: UNIFIED HISTORY TIMELINE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Domain Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = activeDomainFilter == HistoryDomain.ALL,
                            onClick = { viewModel.setHistoryDomainFilter(HistoryDomain.ALL) },
                            label = { Text("Sabhi (All)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = activeDomainFilter == HistoryDomain.KHATA,
                            onClick = { viewModel.setHistoryDomainFilter(HistoryDomain.KHATA) },
                            label = { Text("Udhar Khata", fontSize = 12.sp) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Rose600))
                            }
                        )
                        FilterChip(
                            selected = activeDomainFilter == HistoryDomain.LOANS,
                            onClick = { viewModel.setHistoryDomainFilter(HistoryDomain.LOANS) },
                            label = { Text("Byaaj Loans", fontSize = 12.sp) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Indigo600))
                            }
                        )
                        FilterChip(
                            selected = activeDomainFilter == HistoryDomain.ACCOUNTS,
                            onClick = { viewModel.setHistoryDomainFilter(HistoryDomain.ACCOUNTS) },
                            label = { Text("Accounts", fontSize = 12.sp) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Emerald600))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Koi transaction history nahi hai", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(historyList, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = when (item.domain) {
                                                    HistoryDomain.KHATA -> Rose600.copy(alpha = 0.15f)
                                                    HistoryDomain.LOANS -> Indigo600.copy(alpha = 0.15f)
                                                    else -> Emerald600.copy(alpha = 0.15f)
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = when (item.domain) {
                                                            HistoryDomain.KHATA -> Icons.Default.MenuBook
                                                            HistoryDomain.LOANS -> Icons.Default.AccountBalance
                                                            else -> Icons.Default.AccountBalanceWallet
                                                        },
                                                        contentDescription = null,
                                                        tint = when (item.domain) {
                                                            HistoryDomain.KHATA -> Rose600
                                                            HistoryDomain.LOANS -> Indigo600
                                                            else -> Emerald600
                                                        },
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = item.title,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                if (item.subtitle.isNotBlank()) {
                                                    Text(
                                                        text = item.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = "${item.badgeText} • ${dateFormatter.format(Date(item.date))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${if (item.isCredit) "+" else "-"}₹%,.0f".format(Locale.ENGLISH, item.amount),
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (item.isCredit) Emerald600 else Rose600
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: ANALYTICS & AUDITS
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Section 1: Udhar Khata Summary
                    item {
                        Text("Udhar Khata Hisab Kitab", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Kul Udhar Diya", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalUdharGiven), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Rose600)
                                    }
                                    Column {
                                        Text("Kul Payment Mila", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalUdharReceived), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald600)
                                    }
                                    Column {
                                        Text("Net Lene Baki", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalUdharPending), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Rose600)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                val recoveryRate = if (totalUdharGiven > 0) (totalUdharReceived / totalUdharGiven).toFloat().coerceIn(0f, 1f) else 1f
                                Text("Udhar Recovery Rate: ${(recoveryRate * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { recoveryRate },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Emerald600,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    // Section 2: Interest Loans Portfolio
                    item {
                        Text("Interest Loan Lending Portfolio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Active Principal Lent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalLentPrincipal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Indigo600)
                                    }
                                    Column {
                                        Text("Byaaj Accrued", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalInterestAccrued), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                    }
                                    Column {
                                        Text("Collected Repayments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalRepaymentsCollected), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald600)
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Cashflow & Expenses
                    item {
                        Text("Personal Finance Cashflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Total Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalIncome), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald600)
                                    }
                                    Column {
                                        Text("Total Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, totalExpense), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Rose600)
                                    }
                                    Column {
                                        Text("Net Cashflow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹%,.0f".format(Locale.ENGLISH, netCashflow), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (netCashflow >= 0) Emerald600 else Rose600)
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: Expense Categories
                    item {
                        Text("Expenses by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    if (expenseCategories.isEmpty()) {
                        item {
                            Text("No expense transactions recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(expenseCategories.entries.toList(), key = { it.key }) { entry ->
                            val amount = entry.value
                            val fraction = if (totalExpense > 0) (amount / totalExpense).toFloat().coerceIn(0f, 1f) else 0f
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(entry.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("₹%,.0f (${(fraction * 100).toInt()}%)".format(Locale.ENGLISH, amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.error,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Export Button
                    item {
                        Button(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Full Audit CSV Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        val csvData = remember { viewModel.generateCsvExport() }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Complete Ledger Export") },
            text = {
                Column {
                    Text("Contains all Udhar Khata, Interest Loan, and Accounts entries:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                    ) {
                        Text(csvData.take(500) + if (csvData.length > 500) "..." else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, csvData)
                            type = "text/csv"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Ledger CSV")
                        context.startActivity(shareIntent)
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Share CSV File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
