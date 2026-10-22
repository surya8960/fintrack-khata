package com.example.fintrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fintrack.data.model.Transaction
import com.example.fintrack.data.model.TransactionType
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: KhataViewModel,
    modifier: Modifier = Modifier
) {
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val activeFilter by viewModel.transactionFilterType.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val accountsMap = remember(accounts) { accounts.associateBy { it.id } }

    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transactions History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${filteredTransactions.size} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setNavIndex(0) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV", tint = Emerald600)
                    }
                    IconButton(onClick = { viewModel.openAddTransactionDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Transaction", tint = Emerald600)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddTransactionDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Transaction") },
                containerColor = Emerald600,
                contentColor = Color.White
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = activeFilter == null,
                        onClick = { viewModel.setTransactionFilter(null) },
                        label = { Text("All") }
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == TransactionType.INCOME,
                        onClick = { viewModel.setTransactionFilter(TransactionType.INCOME) },
                        label = { Text("Income") }
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == TransactionType.EXPENSE,
                        onClick = { viewModel.setTransactionFilter(TransactionType.EXPENSE) },
                        label = { Text("Expense") }
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == TransactionType.TRANSFER,
                        onClick = { viewModel.setTransactionFilter(TransactionType.TRANSFER) },
                        label = { Text("Transfer") }
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No transactions found", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap + New Transaction to log expenses or income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = filteredTransactions, key = { it.id }) { tx ->
                        val accountName = accountsMap[tx.accountId]?.name ?: "Account"
                        TransactionListItem(
                            transaction = tx,
                            accountName = accountName,
                            onDelete = { viewModel.deleteTransaction(tx.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        val csvData = remember { viewModel.generateCsvExport() }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("CSV Export Ready") },
            text = {
                Column {
                    Text("Your transaction history is compiled in standard CSV format:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                    ) {
                        Text(
                            csvData.take(500) + if (csvData.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun TransactionListItem(
    transaction: Transaction,
    accountName: String,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(transaction.dateTimestamp) { dateFormat.format(Date(transaction.dateTimestamp)) }

    val (badgeColor, prefix, icon) = when (transaction.type) {
        TransactionType.INCOME -> Triple(Color(0xFF047857), "+", Icons.Default.ArrowDownward)
        TransactionType.EXPENSE -> Triple(Color(0xFFDC2626), "-", Icons.Default.ArrowUpward)
        TransactionType.TRANSFER -> Triple(Color(0xFF2563EB), "⇄", Icons.Default.SwapHoriz)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(transaction.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" • $accountName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$prefix₹%,.2f".format(Locale.ENGLISH, transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
