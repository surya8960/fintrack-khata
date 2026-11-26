package com.example.fintrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fintrack.data.model.AccountType
import com.example.fintrack.data.model.PaymentType
import com.example.fintrack.data.model.TransactionType
import com.example.fintrack.data.model.UdharType
import com.example.fintrack.domain.calculator.InterestCalculator
import com.example.fintrack.ui.components.DatePickerModal
import com.example.fintrack.ui.screens.*
import com.example.fintrack.ui.theme.Amber500
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Indigo600
import com.example.fintrack.ui.theme.Rose600
import com.example.fintrack.ui.viewmodel.KhataViewModel
import java.util.Locale

@Composable
fun MainScreen(viewModel: KhataViewModel) {
    val currentNavIndex by viewModel.currentNavIndex.collectAsState()
    var inUdharCustomerDetail by remember { mutableStateOf(false) }
    var inLoanBorrowerDetail by remember { mutableStateOf(false) }

    // Dialog state
    val showAddUdharCustomerDialog by viewModel.showAddUdharCustomerDialog.collectAsState()
    val showRecordUdharEntryDialog by viewModel.showRecordUdharEntryDialog.collectAsState()
    val activeUdharEntryType by viewModel.activeUdharEntryType.collectAsState()
    val selectedUdharCustomer by viewModel.selectedUdharCustomer.collectAsState()

    val showAddCustomerDialog by viewModel.showAddCustomerDialog.collectAsState()
    val showPaymentDialog by viewModel.showPaymentDialog.collectAsState()
    val activePaymentType by viewModel.activePaymentType.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()

    val showAddAccountDialog by viewModel.showAddAccountDialog.collectAsState()
    val showAddTransactionDialog by viewModel.showAddTransactionDialog.collectAsState()
    val showAddBudgetDialog by viewModel.showAddBudgetDialog.collectAsState()
    val showAddGoalDialog by viewModel.showAddGoalDialog.collectAsState()
    val selectedGoalForContribute by viewModel.selectedGoalForContribute.collectAsState()

    val accounts by viewModel.accounts.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUiMessage()
        }
    }

    // Handle Back button
    BackHandler(enabled = inUdharCustomerDetail || inLoanBorrowerDetail || currentNavIndex > 4) {
        when {
            inUdharCustomerDetail -> inUdharCustomerDetail = false
            inLoanBorrowerDetail -> inLoanBorrowerDetail = false
            currentNavIndex > 4 -> viewModel.setNavIndex(4) // return to More menu
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .testTag("bottom_nav_bar")
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // 1. Home
                NavigationBarItem(
                    selected = currentNavIndex == 0 && !inUdharCustomerDetail && !inLoanBorrowerDetail,
                    onClick = {
                        viewModel.setNavIndex(0)
                        inUdharCustomerDetail = false
                        inLoanBorrowerDetail = false
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_item_home")
                )

                // 2. Khata (Udhar Customers)
                NavigationBarItem(
                    selected = (currentNavIndex == 1 || inUdharCustomerDetail),
                    onClick = {
                        viewModel.setNavIndex(1)
                        inLoanBorrowerDetail = false
                    },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Khata") },
                    label = { Text("Khata", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_item_khata")
                )

                // 3. Loans (Interest Borrowers)
                NavigationBarItem(
                    selected = (currentNavIndex == 2 || inLoanBorrowerDetail),
                    onClick = {
                        viewModel.setNavIndex(2)
                        inUdharCustomerDetail = false
                    },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Loans") },
                    label = { Text("Loans", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_item_loans")
                )

                // 4. Reports (Unified History & Analytics)
                NavigationBarItem(
                    selected = currentNavIndex == 3 && !inUdharCustomerDetail && !inLoanBorrowerDetail,
                    onClick = {
                        viewModel.setNavIndex(3)
                        inUdharCustomerDetail = false
                        inLoanBorrowerDetail = false
                    },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                    label = { Text("Reports", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_item_reports")
                )

                // 5. More (Accounts, Budgets, Settings)
                NavigationBarItem(
                    selected = (currentNavIndex >= 4) && !inUdharCustomerDetail && !inLoanBorrowerDetail,
                    onClick = {
                        viewModel.setNavIndex(4)
                        inUdharCustomerDetail = false
                        inLoanBorrowerDetail = false
                    },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More") },
                    label = { Text("More", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_item_more")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                inUdharCustomerDetail -> {
                    UdharCustomerDetailScreen(
                        viewModel = viewModel,
                        onBack = { inUdharCustomerDetail = false }
                    )
                }
                inLoanBorrowerDetail -> {
                    CustomerDetailScreen(
                        viewModel = viewModel,
                        onBack = { inLoanBorrowerDetail = false },
                        onOpenCalculator = {
                            inLoanBorrowerDetail = false
                            viewModel.setNavIndex(7) // Calculator
                        }
                    )
                }
                else -> {
                    when (currentNavIndex) {
                        0 -> DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToUdharKhata = { viewModel.setNavIndex(1) },
                            onNavigateToLoans = { viewModel.setNavIndex(2) },
                            onNavigateToReports = { viewModel.setNavIndex(3) },
                            onNavigateToAccounts = { viewModel.setNavIndex(5) },
                            onNavigateToBudgets = { viewModel.setNavIndex(6) },
                            onNavigateToCalculator = { viewModel.setNavIndex(7) },
                            onNavigateToSync = { viewModel.setNavIndex(8) }
                        )
                        1 -> UdharKhataScreen(
                            viewModel = viewModel,
                            onCustomerClick = { customerId ->
                                viewModel.selectUdharCustomer(customerId)
                                inUdharCustomerDetail = true
                            }
                        )
                        2 -> CustomerListScreen(
                            viewModel = viewModel,
                            onCustomerClick = { borrowerId ->
                                viewModel.selectCustomer(borrowerId)
                                inLoanBorrowerDetail = true
                            },
                            onCalculateInterestClick = { borrowerId ->
                                viewModel.selectCustomer(borrowerId)
                                viewModel.setNavIndex(7)
                            }
                        )
                        3 -> ReportsScreen(
                            viewModel = viewModel
                        )
                        4 -> MoreScreen(
                            viewModel = viewModel,
                            onNavigateToAccounts = { viewModel.setNavIndex(5) },
                            onNavigateToBudgets = { viewModel.setNavIndex(6) },
                            onNavigateToCalculator = { viewModel.setNavIndex(7) },
                            onNavigateToSync = { viewModel.setNavIndex(8) }
                        )
                        5 -> AccountsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.setNavIndex(4) }
                        )
                        6 -> BudgetsGoalsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.setNavIndex(4) }
                        )
                        7 -> InterestCalculatorScreen(
                            viewModel = viewModel,
                            onAccrualSaved = {
                                viewModel.setNavIndex(2)
                            },
                            onBack = { viewModel.setNavIndex(4) }
                        )
                        8 -> CloudSyncScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.setNavIndex(4) }
                        )
                        else -> DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToUdharKhata = { viewModel.setNavIndex(1) },
                            onNavigateToLoans = { viewModel.setNavIndex(2) },
                            onNavigateToReports = { viewModel.setNavIndex(3) },
                            onNavigateToAccounts = { viewModel.setNavIndex(5) },
                            onNavigateToBudgets = { viewModel.setNavIndex(6) },
                            onNavigateToCalculator = { viewModel.setNavIndex(7) },
                            onNavigateToSync = { viewModel.setNavIndex(8) }
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOGS: Udhar Khata
    // ==========================================

    // Dialog: Add Udhar Customer
    if (showAddUdharCustomerDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.closeAddUdharCustomerDialog() },
            title = { Text("Naya Udhar Grahak Jodein", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Grahak Ka Naam *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_udhar_customer_name")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("input_udhar_customer_phone")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dukan / Ghar Ka Pata") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addUdharCustomer(name, phone, address, notes) },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    modifier = Modifier.testTag("btn_save_udhar_customer")
                ) {
                    Text("Grahak Jodein", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddUdharCustomerDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Record Udhar Entry (Udhar Diya vs Payment Mila)
    if (showRecordUdharEntryDialog && selectedUdharCustomer != null) {
        val cust = selectedUdharCustomer!!
        val isGave = activeUdharEntryType == UdharType.GAVE

        var amountStr by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var billNo by remember { mutableStateOf("") }
        var entryDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var showDatePicker by remember { mutableStateOf(false) }

        val localDate = InterestCalculator.millisToLocalDate(entryDateMs)

        AlertDialog(
            onDismissRequest = { viewModel.closeRecordUdharEntryDialog() },
            title = {
                Text(
                    text = if (isGave) "Udhar Diya (You Gave)" else "Payment Mila (You Got)",
                    fontWeight = FontWeight.Bold,
                    color = if (isGave) Rose600 else Emerald600
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Grahak: ${cust.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Rakam (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_udhar_entry_amount")
                    )

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tareekh (Date)", style = MaterialTheme.typography.labelSmall)
                                Text("$localDate", fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date", tint = if (isGave) Rose600 else Emerald600)
                        }
                    }

                    OutlinedTextField(
                        value = billNo,
                        onValueChange = { billNo = it },
                        label = { Text("Bill / Parchi No. (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Details / Samaan ki jaankari") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordUdharEntry(amount, entryDateMs, note, billNo)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isGave) Rose600 else Emerald600),
                    modifier = Modifier.testTag("btn_save_udhar_entry")
                ) {
                    Text("Darj Karein (Save)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeRecordUdharEntryDialog() }) {
                    Text("Cancel")
                }
            }
        )

        if (showDatePicker) {
            DatePickerModal(
                initialDateMillis = entryDateMs,
                onDateSelected = {
                    entryDateMs = it
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }

    // ==========================================
    // DIALOGS: Interest Loans
    // ==========================================

    // Dialog: Add Loan Borrower
    if (showAddCustomerDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var principalStr by remember { mutableStateOf("") }
        var rateStr by remember { mutableStateOf("5.0") }
        var startDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var notes by remember { mutableStateOf("") }
        var showDatePicker by remember { mutableStateOf(false) }

        val startDate = InterestCalculator.millisToLocalDate(startDateMs)

        AlertDialog(
            onDismissRequest = { viewModel.closeAddCustomerDialog() },
            title = { Text("Naya Interest Loan Banayein", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Borrower Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_name_input")
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_phone_input")
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address / Pata") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = principalStr,
                        onValueChange = { principalStr = it },
                        label = { Text("Mool Dhan (Principal Amount ₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_principal_input")
                    )

                    OutlinedTextField(
                        value = rateStr,
                        onValueChange = { rateStr = it },
                        label = { Text("Monthly Interest Rate (%)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Default 5.0% per month simple interest") },
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_rate_input")
                    )

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .testTag("add_customer_start_date_picker"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Loan Start Date", style = MaterialTheme.typography.labelSmall)
                                Text("$startDate", fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick start date", tint = Indigo600)
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Loan Purpose / Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val principal = principalStr.toDoubleOrNull() ?: 0.0
                        val rate = rateStr.toDoubleOrNull() ?: 5.0
                        viewModel.addCustomer(
                            name = name,
                            phone = phone,
                            address = address,
                            principal = principal,
                            rate = rate,
                            startDate = startDateMs,
                            notes = notes
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                    modifier = Modifier.testTag("btn_save_new_customer")
                ) {
                    Text("Save Loan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddCustomerDialog() }) {
                    Text("Cancel")
                }
            }
        )

        if (showDatePicker) {
            DatePickerModal(
                initialDateMillis = startDateMs,
                onDateSelected = {
                    startDateMs = it
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }

    // Dialog: Record Loan Payment (Interest Payment vs Principal Repayment)
    if (showPaymentDialog && selectedCustomer != null) {
        val cust = selectedCustomer!!
        var amountStr by remember { mutableStateOf("") }
        var paymentMode by remember { mutableStateOf("CASH") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.closePaymentDialog() },
            title = {
                Text(
                    text = activePaymentType.title,
                    fontWeight = FontWeight.Bold,
                    color = if (activePaymentType == PaymentType.INTEREST_PAYMENT) Amber500 else Indigo600
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Borrower: ${cust.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (activePaymentType == PaymentType.INTEREST_PAYMENT) {
                        Text(
                            text = "• Accrued Interest Due: ₹%,.2f\n• Interest payment reduces interest balance ONLY. Principal remains fixed.".format(
                                Locale.ENGLISH, cust.outstandingInterest
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "• Current Principal Balance: ₹%,.2f\n• Principal repayment reduces principal balance ONLY. Accrued interest is untouched.".format(
                                Locale.ENGLISH, cust.remainingPrincipal
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Payment Amount (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("CASH", "UPI", "BANK").forEach { mode ->
                            Button(
                                onClick = { paymentMode = mode },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paymentMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (paymentMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(mode, fontSize = 12.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Payment Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordPayment(amount, paymentMode, notes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (activePaymentType == PaymentType.INTEREST_PAYMENT) Amber500 else Indigo600),
                    modifier = Modifier.testTag("btn_confirm_record_payment")
                ) {
                    Text("Record Payment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closePaymentDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // DIALOGS: Personal Finance (Accounts, Budgets, Goals)
    // ==========================================

    // Dialog: Add Account
    if (showAddAccountDialog) {
        var name by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(AccountType.BANK) }
        var balanceStr by remember { mutableStateOf("") }
        var accNumber by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.closeAddAccountDialog() },
            title = { Text("Add New Account / Wallet", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name * (e.g. SBI, Cash Box)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = balanceStr,
                        onValueChange = { balanceStr = it },
                        label = { Text("Initial Balance (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accNumber,
                        onValueChange = { accNumber = it },
                        label = { Text("Account / UPI ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val balance = balanceStr.toDoubleOrNull() ?: 0.0
                        viewModel.addAccount(name, selectedType, balance, accNumber, "#047857")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Save Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddAccountDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Add Transaction
    if (showAddTransactionDialog) {
        var title by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
        var selectedCategory by remember { mutableStateOf("Food & Grocery") }
        var note by remember { mutableStateOf("") }
        var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }

        AlertDialog(
            onDismissRequest = { viewModel.closeAddTransactionDialog() },
            title = { Text("Add Personal Finance Entry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Transaction Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedType = TransactionType.EXPENSE },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedType == TransactionType.EXPENSE) Rose600 else MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Expense", color = if (selectedType == TransactionType.EXPENSE) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                        Button(
                            onClick = { selectedType = TransactionType.INCOME },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedType == TransactionType.INCOME) Emerald600 else MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Income", color = if (selectedType == TransactionType.INCOME) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.addTransaction(selectedAccountId, null, title, amount, selectedType, selectedCategory, note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Record Entry")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddTransactionDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Add Budget
    if (showAddBudgetDialog) {
        var category by remember { mutableStateOf("") }
        var limitStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.closeAddBudgetDialog() },
            title = { Text("Set Monthly Category Budget", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g. Rent, Groceries, Dining) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = limitStr,
                        onValueChange = { limitStr = it },
                        label = { Text("Monthly Spending Limit (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = limitStr.toDoubleOrNull() ?: 0.0
                        viewModel.addBudget(category, limit, "CURRENT")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Text("Save Budget")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddBudgetDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Add Goal
    if (showAddGoalDialog) {
        var name by remember { mutableStateOf("") }
        var targetStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.closeAddGoalDialog() },
            title = { Text("Create Savings Goal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Goal Name * (e.g. Shop Renovation)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it },
                        label = { Text("Target Amount (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetStr.toDoubleOrNull() ?: 0.0
                        viewModel.addGoal(name, target, 0.0, 90, "#2563EB")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Create Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddGoalDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Contribute to Goal
    if (selectedGoalForContribute != null) {
        val goal = selectedGoalForContribute!!
        var amountStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.closeContributeGoalDialog() },
            title = { Text("Add Funds to '${goal.name}'", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target: ₹%,.0f • Saved so far: ₹%,.0f".format(Locale.ENGLISH, goal.targetAmount, goal.currentAmount))
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Contribution Amount (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.contributeToGoal(goal.id, amount)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Add Savings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeContributeGoalDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
