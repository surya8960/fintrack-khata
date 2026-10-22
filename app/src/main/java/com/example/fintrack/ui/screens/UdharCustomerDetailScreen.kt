package com.example.fintrack.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.data.model.UdharEntry
import com.example.fintrack.data.model.UdharType
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Rose600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdharCustomerDetailScreen(
    viewModel: KhataViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val customer by viewModel.selectedUdharCustomer.collectAsState()
    val entries by viewModel.selectedCustomerUdharEntries.collectAsState()
    val allEntries by viewModel.udharEntries.collectAsState()

    var showDeleteCustomerDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<UdharEntry?>(null) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.ENGLISH) }

    if (customer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val cust = customer!!
    val currentBalance = remember(cust, allEntries) { cust.calculateBalance(allEntries) }
    val totalGave = remember(cust, allEntries) { cust.calculateTotalGave(allEntries) }
    val totalGot = remember(cust, allEntries) { cust.calculateTotalGot(allEntries) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cust.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (cust.phone.isNotBlank()) {
                            Text(cust.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_customer_detail")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // WhatsApp Payment Reminder button
                    IconButton(
                        onClick = {
                            val reminderText = "Namaste ${cust.name} ji, aapka Udhar Khata me baki hisab ₹%,.0f hai. Kripya samay par bhugtan karein. Dhanyawad - FinTrack Khata".format(Locale.ENGLISH, currentBalance)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, reminderText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Send Payment Reminder")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.testTag("btn_share_reminder")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Reminder")
                    }

                    // Delete Customer
                    IconButton(
                        onClick = { showDeleteCustomerDialog = true },
                        modifier = Modifier.testTag("btn_delete_customer")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Customer")
                    }
                }
            )
        },
        bottomBar = {
            // Dual Action Buttons: Udhar Diya (Red) & Payment Mila (Green)
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Red Button: You Gave / Udhar Diya
                    Button(
                        onClick = { viewModel.openRecordUdharEntryDialog(UdharType.GAVE) },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_udhar_diya")
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Udhar Diya (₹)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    // Green Button: You Got / Payment Mila
                    Button(
                        onClick = { viewModel.openRecordUdharEntryDialog(UdharType.GOT) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_payment_mila")
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payment Mila (₹)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Running Balance Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        currentBalance > 0 -> Rose600.copy(alpha = 0.1f)
                        currentBalance < 0 -> Emerald600.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            currentBalance > 0 -> "Aapko Lena Hai (You'll Get)"
                            currentBalance < 0 -> "Aapko Dena Hai (You'll Give)"
                            else -> "Hisab Barabar (Settled)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            currentBalance > 0 -> Rose600
                            currentBalance < 0 -> Emerald600
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹%,.0f".format(Locale.ENGLISH, if (currentBalance < 0) -currentBalance else currentBalance),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            currentBalance > 0 -> Rose600
                            currentBalance < 0 -> Emerald600
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (cust.address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📍 ${cust.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kul Udhar Diya", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalGave), fontWeight = FontWeight.Bold, color = Rose600)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kul Payment Mila", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("₹%,.0f".format(Locale.ENGLISH, totalGot), fontWeight = FontWeight.Bold, color = Emerald600)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kul Entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${entries.size}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hisab Timeline (${entries.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Abhi tak koi entry nahi hai", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Neeche diye buttons se Udhar ya Payment jodein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val isGave = entry.type == UdharType.GAVE
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
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
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isGave) Rose600.copy(alpha = 0.15f) else Emerald600.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isGave) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = if (isGave) Rose600 else Emerald600,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = if (isGave) "Udhar Diya" else "Payment Mila",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isGave) Rose600 else Emerald600
                                        )
                                        if (entry.note.isNotBlank() || entry.billNumber.isNotBlank()) {
                                            val noteStr = buildString {
                                                if (entry.billNumber.isNotBlank()) append("Bill #${entry.billNumber} • ")
                                                append(entry.note)
                                            }
                                            Text(
                                                text = noteStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = dateFormatter.format(Date(entry.date)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${if (isGave) "-" else "+"}₹%,.0f".format(Locale.ENGLISH, entry.amount),
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isGave) Rose600 else Emerald600
                                    )

                                    IconButton(
                                        onClick = { entryToDelete = entry },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Customer Confirmation
    if (showDeleteCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomerDialog = false },
            title = { Text("Grahak Delete Karein?") },
            text = { Text("Kya aap '${cust.name}' aur unka poora hisab kitab delete karna chahte hain?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUdharCustomer(cust.id)
                        showDeleteCustomerDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose600)
                ) {
                    Text("Haan, Delete Karein", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCustomerDialog = false }) {
                    Text("Nahi")
                }
            }
        )
    }

    // Delete Entry Confirmation
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Entry Delete Karein?") },
            text = { Text("Kya aap ₹%,.0f ki ye entry delete karna chahte hain?".format(Locale.ENGLISH, entryToDelete!!.amount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUdharEntry(entryToDelete!!.id)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose600)
                ) {
                    Text("Haan, Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Radd Karein")
                }
            }
        )
    }
}
