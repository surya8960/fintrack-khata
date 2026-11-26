package com.example.fintrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.domain.calculator.InterestCalculator
import com.example.fintrack.ui.components.DatePickerModal
import com.example.fintrack.ui.theme.DueColor
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Indigo600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestCalculatorScreen(
    viewModel: KhataViewModel,
    onAccrualSaved: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val customers by viewModel.customers.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val startDateMs by viewModel.calcStartDate.collectAsState()
    val endDateMs by viewModel.calcEndDate.collectAsState()
    val calcRate by viewModel.calcRate.collectAsState()
    val preview by viewModel.calculationPreview.collectAsState()
    val showConfirmDialog by viewModel.showConfirmAccrualDialog.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    val startDate = InterestCalculator.millisToLocalDate(startDateMs)
    val endDate = InterestCalculator.millisToLocalDate(endDateMs)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("interest_calculator_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Screen Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Simple Interest Calculator",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Custom date ranges • Fixed principal • No compounding",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Customer Selection
        item {
            ExposedDropdownMenuBox(
                expanded = customerDropdownExpanded,
                onExpandedChange = { customerDropdownExpanded = !customerDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCustomer?.let { "${it.name} (Principal: ₹%,.0f)".format(Locale.ENGLISH, it.principalAmount) } ?: "Select a Customer Loan",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Customer Loan") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .testTag("customer_selector")
                )

                ExposedDropdownMenu(
                    expanded = customerDropdownExpanded,
                    onDismissRequest = { customerDropdownExpanded = false }
                ) {
                    customers.forEach { cust ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(cust.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Principal: ₹%,.0f • Rate: %.1f%%/mo".format(
                                            Locale.ENGLISH,
                                            cust.principalAmount,
                                            cust.monthlyInterestRate
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = {
                                viewModel.selectCustomer(cust.id)
                                customerDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedCustomer != null) {
            val customer = selectedCustomer!!

            // Fixed Principal & Details Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Fixed Principal Basis",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹%,.2f".format(Locale.ENGLISH, customer.principalAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600
                            )
                            Text(
                                text = "Simple interest computed on fixed principal only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Indigo600.copy(alpha = 0.15f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Monthly Rate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Indigo600
                                )
                                Text(
                                    text = "%.2f%%".format(Locale.ENGLISH, customer.monthlyInterestRate),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Indigo600
                                )
                            }
                        }
                    }
                }
            }

            // Custom Dates & Rate Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Custom Calculation Period",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Start Date Picker Field
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showStartDatePicker = true }
                                .testTag("btn_select_start_date"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Interest Start Date",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$startDate",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Pick start date",
                                    tint = Emerald600
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // End Date Picker Field
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEndDatePicker = true }
                                .testTag("btn_select_end_date"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Interest End Date",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$endDate",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Pick end date",
                                    tint = Indigo600
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Editable Monthly Rate
                        OutlinedTextField(
                            value = calcRate,
                            onValueChange = { viewModel.setCalcRate(it) },
                            label = { Text("Monthly Interest Rate (%)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calc_rate_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            supportingText = { Text("Default is 5.0%, fully editable") }
                        )

                        // Quick rate chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("2.0", "3.0", "4.0", "5.0", "7.0").forEach { rate ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (calcRate == rate) Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { viewModel.setCalcRate(rate) }
                                ) {
                                    Text(
                                        text = "$rate%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (calcRate == rate) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Calculation Breakdown & Preview Card
            if (preview != null) {
                val res = preview!!

                // Duplicate / Overlap Warning Banner
                if (res.isOverlapping) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DueColor.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = DueColor)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Duplicate Period Detected!",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DueColor
                                    )
                                    Text(
                                        text = res.overlapErrorMessage ?: "This period overlaps with an existing interest entry.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DueColor
                                    )
                                }
                            }
                        }
                    }
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Calculation Breakdown",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Indigo600.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Simple Interest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Indigo600,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Breakdown items
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Monthly Interest Rate:", style = MaterialTheme.typography.bodyMedium)
                                Text("%.2f%% (₹%,.2f/mo)".format(Locale.ENGLISH, res.monthlyRate, res.monthlyInterestAmount), fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Completed Monthly Periods:", style = MaterialTheme.typography.bodyMedium)
                                Text("%d month(s) = ₹%,.2f".format(Locale.ENGLISH, res.completedMonths, res.fullMonthsInterest), fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Partial Period:", style = MaterialTheme.typography.bodyMedium)
                                if (res.partialDays > 0) {
                                    Text("%d/%d days = ₹%,.2f".format(Locale.ENGLISH, res.partialDays, res.daysInPartialMonth, res.partialMonthInterest), fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text("0 days (exact full month)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Total Accrued Result
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Accrued Interest:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "₹%,.2f".format(Locale.ENGLISH, res.totalAccruedInterest),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Indigo600
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Principal remains fixed at ₹%,.2f (separate from interest)".format(Locale.ENGLISH, res.principalBasis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Review & Confirm Button
                            Button(
                                onClick = { viewModel.setShowConfirmAccrualDialog(true) },
                                enabled = !res.isOverlapping && res.totalAccruedInterest > 0.0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_review_confirm_accrual"),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Review & Record Interest", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Please select a customer loan above to calculate interest.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Start Date Picker Modal
    if (showStartDatePicker) {
        DatePickerModal(
            initialDateMillis = startDateMs,
            onDateSelected = {
                viewModel.setCalcStartDate(it)
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    // End Date Picker Modal
    if (showEndDatePicker) {
        DatePickerModal(
            initialDateMillis = endDateMs,
            onDateSelected = {
                viewModel.setCalcEndDate(it)
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    // Confirmation Review Dialog
    if (showConfirmDialog && preview != null && selectedCustomer != null) {
        val res = preview!!
        val cust = selectedCustomer!!

        AlertDialog(
            onDismissRequest = { viewModel.setShowConfirmAccrualDialog(false) },
            title = {
                Text("Confirm Interest Accrual", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Please review the interest calculation for ${cust.name}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Period: ${res.startDate} to ${res.endDate}")
                    Text("• Fixed Principal: ₹%,.2f".format(Locale.ENGLISH, res.principalBasis))
                    Text("• Rate: %.2f%% per month".format(Locale.ENGLISH, res.monthlyRate))
                    Text("• Completed Months: ${res.completedMonths}")
                    if (res.partialDays > 0) {
                        Text("• Partial Days: ${res.partialDays} / ${res.daysInPartialMonth}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Accrued Interest to Add: ₹%,.2f".format(Locale.ENGLISH, res.totalAccruedInterest),
                        fontWeight = FontWeight.Bold,
                        color = Indigo600
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Principal will NOT change. Accrued interest will be saved into ${cust.name}'s interest history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val recorded = viewModel.confirmRecordAccrual()
                        if (recorded) {
                            onAccrualSaved()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                    modifier = Modifier.testTag("confirm_record_interest_btn")
                ) {
                    Text("Confirm & Record")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.setShowConfirmAccrualDialog(false) },
                    modifier = Modifier.testTag("cancel_record_interest_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
