package com.example.fintrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.data.model.HistoryDomain
import com.example.fintrack.data.model.UnifiedHistoryItem
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Indigo600
import com.example.fintrack.ui.theme.Rose600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: KhataViewModel,
    onNavigateToUdharKhata: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onNavigateToSync: () -> Unit
) {
    // Data collections
    val udharCustomers by viewModel.udharCustomers.collectAsState()
    val udharEntries by viewModel.udharEntries.collectAsState()

    val loanBorrowers by viewModel.customers.collectAsState()
    val loanPayments by viewModel.payments.collectAsState()
    val loanAccruals by viewModel.interestAccruals.collectAsState()

    val accounts by viewModel.accounts.collectAsState()
    val recentHistory by viewModel.unifiedHistoryList.collectAsState()
    val userSession by viewModel.userSession.collectAsState()

    // Calculations for Udhar Khata
    val totalUdharGave = remember(udharCustomers, udharEntries) {
        udharCustomers.sumOf { it.calculateTotalGave(udharEntries) }
    }
    val totalUdharGot = remember(udharCustomers, udharEntries) {
        udharCustomers.sumOf { it.calculateTotalGot(udharEntries) }
    }
    val totalUdharPending = (totalUdharGave - totalUdharGot).coerceAtLeast(0.0)

    // Calculations for Interest Loans
    val totalLoanPrincipal = loanBorrowers.sumOf { it.principalAmount }
    val remainingLoanPrincipal = loanBorrowers.sumOf { it.remainingPrincipal }
    val totalLoanInterestAccrued = loanBorrowers.sumOf { it.totalAccruedInterest }
    val totalLoanInterestPaid = loanBorrowers.sumOf { it.totalInterestPaid }
    val outstandingLoanInterest = loanBorrowers.sumOf { it.outstandingInterest }
    val totalLoanDue = remainingLoanPrincipal + outstandingLoanInterest

    // Calculations for Liquid Bank & Cash
    val totalCashAndBank = accounts.sumOf { it.balance }

    // Combined Net Worth
    val totalNetWorth = totalCashAndBank + totalUdharPending + totalLoanDue

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FinTrack Khata",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Udhar Ledger, Interest Loans & Accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (userSession.isAuthenticated) Emerald600.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.3f),
                    modifier = Modifier.clickable { onNavigateToSync() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (userSession.isAuthenticated) Emerald600 else MaterialTheme.colorScheme.tertiary,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (userSession.isAuthenticated) "Cloud Sync" else "Local / Offline",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (userSession.isAuthenticated) Emerald600 else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Aggregate Summary Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Kul Sampatti (Total Net Worth)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "₹%,.2f".format(Locale.ENGLISH, totalNetWorth),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Udhar Baki", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("₹%,.0f".format(Locale.ENGLISH, totalUdharPending), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Rose600)
                        }
                        Column {
                            Text("Loan Capital Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("₹%,.0f".format(Locale.ENGLISH, remainingLoanPrincipal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Indigo600)
                        }
                        Column {
                            Text("Bank & Cash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text("₹%,.0f".format(Locale.ENGLISH, totalCashAndBank), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald600)
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Financial Hubs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // CARD 1: 📕 Udhar Khata (Customer Credit/Debit Ledger)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_udhar_khata")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Rose600.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Rose600, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("📕 Udhar Khata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Customer Credit / Debit Ledger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Rose600.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "${udharCustomers.size} Grahak",
                                style = MaterialTheme.typography.labelSmall,
                                color = Rose600,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Udhar Diya", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalUdharGave), fontWeight = FontWeight.Bold, color = Rose600)
                        }
                        Column {
                            Text("Payment Mila", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalUdharGot), fontWeight = FontWeight.Bold, color = Emerald600)
                        }
                        Column {
                            Text("Kul Lene Baki", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalUdharPending), fontWeight = FontWeight.ExtraBold, color = Rose600)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openAddUdharCustomerDialog() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Naya Grahak", fontSize = 13.sp)
                        }

                        Button(
                            onClick = onNavigateToUdharKhata,
                            colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Khata Kholein", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // CARD 2: 🏦 Interest Loan Khata (Borrower Money Lending)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_interest_loans")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Indigo600.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Indigo600, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("🏦 Interest Loan Khata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Borrower Lending & Monthly Byaaj", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Indigo600.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "${loanBorrowers.size} Borrowers",
                                style = MaterialTheme.typography.labelSmall,
                                color = Indigo600,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Mool Dhan Lent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹%,.0f".format(Locale.ENGLISH, remainingLoanPrincipal), fontWeight = FontWeight.Bold, color = Indigo600)
                        }
                        Column {
                            Text("Byaaj Accrued", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalLoanInterestAccrued), fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                        }
                        Column {
                            Text("Total Amount Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalLoanDue), fontWeight = FontWeight.ExtraBold, color = Indigo600)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openAddCustomerDialog() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Naya Loan", fontSize = 13.sp)
                        }

                        Button(
                            onClick = onNavigateToLoans,
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Loans Kholein", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Quick Navigation Tiles (Accounts, Budgets, Unified History, Calculator)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Personal Finance & Utilities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionTile(
                            title = "Accounts",
                            icon = Icons.Default.AccountBalanceWallet,
                            color = Emerald600,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAccounts
                        )
                        QuickActionTile(
                            title = "Budgets",
                            icon = Icons.Default.PieChart,
                            color = Indigo600,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToBudgets
                        )
                        QuickActionTile(
                            title = "History",
                            icon = Icons.Default.History,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToReports
                        )
                        QuickActionTile(
                            title = "Byaaj Calc",
                            icon = Icons.Default.Calculate,
                            color = Color(0xFFD97706),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToCalculator
                        )
                    }
                }
            }
        }

        // Recent Unified Activity Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Haal Ki Activity (Recent)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToReports) {
                    Text("Sabhi Dekhein", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (recentHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Koi recent activity nahi hai", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(recentHistory.take(5), key = { it.id }) { item ->
                RecentHistoryCard(item = item, dateFormatter = dateFormatter)
            }
        }
    }
}

@Composable
fun QuickActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .clickable(onClick = onClick)
            .height(72.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun RecentHistoryCard(
    item: UnifiedHistoryItem,
    dateFormatter: SimpleDateFormat
) {
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
                    shape = RoundedCornerShape(8.dp),
                    color = when (item.domain) {
                        HistoryDomain.KHATA -> Rose600.copy(alpha = 0.15f)
                        HistoryDomain.LOANS -> Indigo600.copy(alpha = 0.15f)
                        else -> Emerald600.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.size(38.dp)
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${item.badgeText} • ${dateFormatter.format(Date(item.date))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
