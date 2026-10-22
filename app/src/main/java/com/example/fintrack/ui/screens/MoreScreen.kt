package com.example.fintrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Indigo600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.util.Locale

@Composable
fun MoreScreen(
    viewModel: KhataViewModel,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onNavigateToSync: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val goals by viewModel.goals.collectAsState()

    val totalCashAndBank = remember(accounts) { accounts.sumOf { it.balance } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("more_screen")
    ) {
        Text(
            text = "⚙️ More & Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Accounts, Budgets, Goals & Cloud Sync",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Section: Personal Finance Hubs
            item {
                Text(
                    text = "Personal Finance Management",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // 1. Bank Accounts & Cash
            item {
                MoreMenuCard(
                    title = "Bank Accounts & Cash Box",
                    subtitle = "${accounts.size} active accounts • Kul Balance: ₹%,.0f".format(Locale.ENGLISH, totalCashAndBank),
                    icon = Icons.Default.AccountBalance,
                    badgeColor = Emerald600,
                    testTag = "menu_item_accounts",
                    onClick = onNavigateToAccounts
                )
            }

            // 2. Budgets & Goals
            item {
                MoreMenuCard(
                    title = "Category Budgets & Goals",
                    subtitle = "${budgets.size} monthly limits set • ${goals.size} savings goals",
                    icon = Icons.Default.PieChart,
                    badgeColor = Indigo600,
                    testTag = "menu_item_budgets",
                    onClick = onNavigateToBudgets
                )
            }

            // Section: Financial Tools
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Financial Calculators & Utilities",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // 3. Simple Interest Calculator
            item {
                MoreMenuCard(
                    title = "Monthly Byaaj Calculator",
                    subtitle = "Simple interest calculation (Principal × Rate ÷ 100)",
                    icon = Icons.Default.Calculate,
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    testTag = "menu_item_calculator",
                    onClick = onNavigateToCalculator
                )
            }

            // 4. Cloud Sync & Backup
            item {
                MoreMenuCard(
                    title = "Cloud Sync & Data Backup",
                    subtitle = "Supabase real-time sync, local backup & restore",
                    icon = Icons.Default.CloudSync,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    testTag = "menu_item_sync",
                    onClick = onNavigateToSync
                )
            }

            // Section: App Info & Indian Business Settings
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "App Preferences",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Currency", fontWeight = FontWeight.SemiBold)
                            Text("₹ Indian Rupee (INR)", color = Emerald600, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Date Format", fontWeight = FontWeight.SemiBold)
                            Text("DD/MM/YYYY", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Language", fontWeight = FontWeight.SemiBold)
                            Text("English & Hinglish", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
