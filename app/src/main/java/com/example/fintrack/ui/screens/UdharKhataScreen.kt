package com.example.fintrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.data.model.UdharCustomer
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Rose600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.util.Locale

@Composable
fun UdharKhataScreen(
    viewModel: KhataViewModel,
    onCustomerClick: (String) -> Unit
) {
    val customers by viewModel.filteredUdharCustomers.collectAsState()
    val allEntries by viewModel.udharEntries.collectAsState()
    val allCustomers by viewModel.udharCustomers.collectAsState()
    val searchQuery by viewModel.udharSearchQuery.collectAsState()
    val activeFilter by viewModel.udharFilter.collectAsState()

    // Aggregate stats
    val totalGave = remember(allCustomers, allEntries) {
        allCustomers.sumOf { it.calculateTotalGave(allEntries) }
    }
    val totalGot = remember(allCustomers, allEntries) {
        allCustomers.sumOf { it.calculateTotalGot(allEntries) }
    }
    val totalPending = (totalGave - totalGot).coerceAtLeast(0.0)

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddUdharCustomerDialog() },
                containerColor = Rose600,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer") },
                text = { Text("+ Naya Grahak", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_add_udhar_customer")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .testTag("udhar_khata_screen")
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "📕 Udhar Khata",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Grahak Credit / Debit Hisab Kitab",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = "${allCustomers.size} Grahak",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Total Given (Red)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Udhar Diya", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "₹%,.0f".format(Locale.ENGLISH, totalGave),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Rose600
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Total Received (Green)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Payment Mila", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "₹%,.0f".format(Locale.ENGLISH, totalGot),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Total Pending (Lene Baki)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Kul Lene Baki", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "₹%,.0f".format(Locale.ENGLISH, totalPending),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalPending > 0) Rose600 else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setUdharSearchQuery(it) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setUdharSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                placeholder = { Text("Grahak ka naam, phone ya pata...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("udhar_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeFilter == "ALL",
                    onClick = { viewModel.setUdharFilter("ALL") },
                    label = { Text("Sabhi (${allCustomers.size})") },
                    modifier = Modifier.testTag("chip_filter_all")
                )
                FilterChip(
                    selected = activeFilter == "PENDING",
                    onClick = { viewModel.setUdharFilter("PENDING") },
                    label = { Text("Lene Baki (Pending)") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Rose600)
                        )
                    },
                    modifier = Modifier.testTag("chip_filter_pending")
                )
                FilterChip(
                    selected = activeFilter == "SETTLED",
                    onClick = { viewModel.setUdharFilter("SETTLED") },
                    label = { Text("Chukta (Settled)") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Emerald600)
                        )
                    },
                    modifier = Modifier.testTag("chip_filter_settled")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Customer List
            if (customers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Koi grahak nahi hai" else "Koi grahak nahi mila",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Neeche diye button se naya grahak jodein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(customers, key = { it.id }) { customer ->
                        val balance = customer.calculateBalance(allEntries)
                        UdharCustomerCard(
                            customer = customer,
                            balance = balance,
                            onClick = {
                                viewModel.selectUdharCustomer(customer.id)
                                onCustomerClick(customer.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UdharCustomerCard(
    customer: UdharCustomer,
    balance: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("udhar_customer_${customer.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar initial
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = customer.name.firstOrNull()?.uppercase() ?: "C",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (customer.phone.isNotBlank()) {
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customer.address.isNotBlank()) {
                        Text(
                            text = customer.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Balance Status Badge
            Column(horizontalAlignment = Alignment.End) {
                when {
                    balance > 0 -> {
                        Text(
                            text = "Lene Baki",
                            style = MaterialTheme.typography.labelSmall,
                            color = Rose600,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "₹%,.0f".format(Locale.ENGLISH, balance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Rose600
                        )
                    }
                    balance < 0 -> {
                        Text(
                            text = "Dene Baki",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald600,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "₹%,.0f".format(Locale.ENGLISH, -balance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald600
                        )
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Hisab Chukta",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
