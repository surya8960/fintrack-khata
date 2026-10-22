package com.example.fintrack.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fintrack.BuildConfig
import com.example.fintrack.data.local.FinanceSnapshot
import com.example.fintrack.data.local.JsonFileStore
import com.example.fintrack.data.local.KhataSnapshot
import com.example.fintrack.data.model.UserSession
import com.example.fintrack.data.supabase.SupabaseService
import com.example.fintrack.data.sync.CloudSyncManager
import com.example.fintrack.data.model.Account
import com.example.fintrack.data.model.AccountType
import com.example.fintrack.data.model.Budget
import com.example.fintrack.data.model.Customer
import com.example.fintrack.data.model.Goal
import com.example.fintrack.data.model.HistoryDomain
import com.example.fintrack.data.model.InterestAccrual
import com.example.fintrack.data.model.Payment
import com.example.fintrack.data.model.PaymentType
import com.example.fintrack.data.model.Transaction
import com.example.fintrack.data.model.TransactionType
import com.example.fintrack.data.model.UdharCustomer
import com.example.fintrack.data.model.UdharEntry
import com.example.fintrack.data.model.UdharType
import com.example.fintrack.data.model.UnifiedHistoryItem
import com.example.fintrack.data.repository.FinanceRepository
import com.example.fintrack.data.repository.KhataRepository
import com.example.fintrack.domain.calculator.CalculationResult
import com.example.fintrack.domain.calculator.InterestCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class KhataViewModel(
    private val khataRepo: KhataRepository = KhataRepository(),
    private val financeRepo: FinanceRepository = FinanceRepository(),
    private val supabase: SupabaseService = SupabaseService()
) : ViewModel() {

    private val syncManager = CloudSyncManager(supabase, khataRepo, financeRepo)

    override fun onCleared() {
        supabase.close()
        super.onCleared()
    }

    // --- Navigation ---
    // 0: Home, 1: Khata (Udhar), 2: Loans (Interest), 3: Reports (History & Analytics), 4: More (Accounts, Budgets, Settings)
    private val _currentNavIndex = MutableStateFlow(0)
    val currentNavIndex: StateFlow<Int> = _currentNavIndex.asStateFlow()

    fun setNavIndex(index: Int) {
        _currentNavIndex.value = index
    }

    // --- Personal Finance State (Accounts, Budgets, Goals, Transactions) ---
    val accounts: StateFlow<List<Account>> = financeRepo.accounts
    val transactions: StateFlow<List<Transaction>> = financeRepo.transactions
    val budgets: StateFlow<List<Budget>> = financeRepo.budgets
    val goals: StateFlow<List<Goal>> = financeRepo.goals

    // --- Udhar Khata State ---
    val udharCustomers: StateFlow<List<UdharCustomer>> = khataRepo.udharCustomers
    val udharEntries: StateFlow<List<UdharEntry>> = khataRepo.udharEntries

    private val _udharSearchQuery = MutableStateFlow("")
    val udharSearchQuery: StateFlow<String> = _udharSearchQuery.asStateFlow()

    // Filter: "ALL", "PENDING" (Lene Baki), "SETTLED" (Chukta)
    private val _udharFilter = MutableStateFlow("ALL")
    val udharFilter: StateFlow<String> = _udharFilter.asStateFlow()

    fun setUdharSearchQuery(query: String) {
        _udharSearchQuery.value = query
    }

    fun setUdharFilter(filter: String) {
        _udharFilter.value = filter
    }

    val filteredUdharCustomers: StateFlow<List<UdharCustomer>> = combine(
        udharCustomers,
        udharEntries,
        udharSearchQuery,
        udharFilter
    ) { customers, entries, query, filter ->
        customers.filter { customer ->
            val matchesQuery = query.isBlank() ||
                customer.name.contains(query, ignoreCase = true) ||
                customer.phone.contains(query) ||
                customer.address.contains(query, ignoreCase = true)

            if (!matchesQuery) return@filter false

            val bal = customer.calculateBalance(entries)
            when (filter) {
                "PENDING" -> bal > 0.005
                "SETTLED" -> kotlin.math.abs(bal) <= 0.005
                else -> true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedUdharCustomerId = MutableStateFlow<String?>(null)
    val selectedUdharCustomerId: StateFlow<String?> = _selectedUdharCustomerId.asStateFlow()

    val selectedUdharCustomer: StateFlow<UdharCustomer?> = combine(
        udharCustomers,
        selectedUdharCustomerId
    ) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedCustomerUdharEntries: StateFlow<List<UdharEntry>> = combine(
        udharEntries,
        selectedUdharCustomerId
    ) { entries, id ->
        if (id == null) emptyList()
        else entries.filter { it.customerId == id }.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectUdharCustomer(id: String?) {
        _selectedUdharCustomerId.value = id
    }

    // --- Interest Loan Khata State ---
    val customers: StateFlow<List<Customer>> = khataRepo.customers // Borrowers
    val interestAccruals: StateFlow<List<InterestAccrual>> = khataRepo.interestAccruals
    val payments: StateFlow<List<Payment>> = khataRepo.payments
    val userSession = khataRepo.userSession

    private val _loanSearchQuery = MutableStateFlow("")
    val loanSearchQuery: StateFlow<String> = _loanSearchQuery.asStateFlow()

    fun setLoanSearchQuery(query: String) {
        _loanSearchQuery.value = query
    }

    // Backwards compatibility alias
    val searchQuery: StateFlow<String> = _loanSearchQuery.asStateFlow()
    fun setSearchQuery(query: String) = setLoanSearchQuery(query)

    val filteredCustomers: StateFlow<List<Customer>> = combine(
        customers,
        loanSearchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.phone.contains(query) ||
            it.address.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transaction Filter for Personal Finance TransactionsScreen
    private val _transactionFilterType = MutableStateFlow<TransactionType?>(null)
    val transactionFilterType: StateFlow<TransactionType?> = _transactionFilterType.asStateFlow()

    fun setTransactionFilter(type: TransactionType?) {
        _transactionFilterType.value = type
    }

    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        transactions,
        transactionFilterType
    ) { list, filter ->
        if (filter == null) list else list.filter { it.type == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCustomerId = MutableStateFlow<String?>(null)
    val selectedCustomerId: StateFlow<String?> = _selectedCustomerId.asStateFlow()

    val selectedCustomer: StateFlow<Customer?> = combine(
        customers,
        selectedCustomerId
    ) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentCustomerAccruals: StateFlow<List<InterestAccrual>> = combine(
        interestAccruals,
        selectedCustomerId
    ) { list, id ->
        if (id == null) emptyList()
        else list.filter { it.customerId == id }.sortedByDescending { it.endDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentCustomerPayments: StateFlow<List<Payment>> = combine(
        payments,
        selectedCustomerId
    ) { list, id ->
        if (id == null) emptyList()
        else list.filter { it.customerId == id }.sortedByDescending { it.paymentDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Unified History Stream & Filter ---
    private val _historyDomainFilter = MutableStateFlow(HistoryDomain.ALL)
    val historyDomainFilter: StateFlow<HistoryDomain> = _historyDomainFilter.asStateFlow()

    fun setHistoryDomainFilter(domain: HistoryDomain) {
        _historyDomainFilter.value = domain
    }

    val unifiedHistoryList: StateFlow<List<UnifiedHistoryItem>> = combine(
        listOf(udharEntries, payments, transactions, historyDomainFilter, udharCustomers, customers, accounts)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val uEntries = values[0] as List<UdharEntry>
        @Suppress("UNCHECKED_CAST")
        val bPayments = values[1] as List<Payment>
        @Suppress("UNCHECKED_CAST")
        val txs = values[2] as List<Transaction>
        val domain = values[3] as HistoryDomain
        @Suppress("UNCHECKED_CAST")
        val uCusts = values[4] as List<UdharCustomer>
        @Suppress("UNCHECKED_CAST")
        val bCusts = values[5] as List<Customer>
        @Suppress("UNCHECKED_CAST")
        val accs = values[6] as List<Account>
        val list = mutableListOf<UnifiedHistoryItem>()

        // 1. Udhar Entries
        if (domain == HistoryDomain.ALL || domain == HistoryDomain.KHATA) {
            uEntries.forEach { entry ->
                val custName = uCusts.find { it.id == entry.customerId }?.name ?: "Customer"
                val isGave = entry.type == UdharType.GAVE
                list.add(
                    UnifiedHistoryItem(
                        id = entry.id,
                        title = custName,
                        subtitle = if (entry.billNumber.isNotBlank()) "Bill #${entry.billNumber} • ${entry.note}" else entry.note.ifBlank { entry.type.hinglishTitle },
                        amount = entry.amount,
                        date = entry.date,
                        domain = HistoryDomain.KHATA,
                        isCredit = !isGave,
                        badgeText = if (isGave) "Udhar Diya" else "Payment Mila",
                        notes = entry.note
                    )
                )
            }
        }

        // 2. Loan Payments & Principal Repayments
        if (domain == HistoryDomain.ALL || domain == HistoryDomain.LOANS) {
            bPayments.forEach { pay ->
                val borrowerName = bCusts.find { it.id == pay.customerId }?.name ?: "Borrower"
                val isInterest = pay.paymentType == PaymentType.INTEREST_PAYMENT
                list.add(
                    UnifiedHistoryItem(
                        id = pay.id,
                        title = "$borrowerName (${pay.paymentType.title})",
                        subtitle = "${pay.paymentMode} • ${pay.notes.ifBlank { "Loan Installment" }}",
                        amount = pay.amount,
                        date = pay.paymentDate,
                        domain = HistoryDomain.LOANS,
                        isCredit = true,
                        badgeText = if (isInterest) "Byaaj Mila" else "Mool Wapas",
                        notes = pay.notes
                    )
                )
            }
        }

        // 3. Personal Finance Transactions
        if (domain == HistoryDomain.ALL || domain == HistoryDomain.ACCOUNTS) {
            txs.forEach { tx ->
                val accName = accs.find { it.id == tx.accountId }?.name ?: "Account"
                val isIncome = tx.type == TransactionType.INCOME
                list.add(
                    UnifiedHistoryItem(
                        id = tx.id,
                        title = tx.title,
                        subtitle = "$accName • ${tx.category}",
                        amount = tx.amount,
                        date = tx.dateTimestamp,
                        domain = HistoryDomain.ACCOUNTS,
                        isCredit = isIncome,
                        badgeText = when (tx.type) {
                            TransactionType.INCOME -> "Income"
                            TransactionType.EXPENSE -> "Expense"
                            TransactionType.TRANSFER -> "Transfer"
                        },
                        notes = tx.note
                    )
                )
            }
        }

        list.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Calculation Engine State for Loans ---
    private val _calcStartDate = MutableStateFlow<Long>(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
    val calcStartDate: StateFlow<Long> = _calcStartDate.asStateFlow()

    private val _calcEndDate = MutableStateFlow<Long>(System.currentTimeMillis())
    val calcEndDate: StateFlow<Long> = _calcEndDate.asStateFlow()

    private val _calcRate = MutableStateFlow("5.0")
    val calcRate: StateFlow<String> = _calcRate.asStateFlow()

    private val _calculationPreview = MutableStateFlow<CalculationResult?>(null)
    val calculationPreview: StateFlow<CalculationResult?> = _calculationPreview.asStateFlow()

    // --- Dialog Visibility States ---
    private val _showAddUdharCustomerDialog = MutableStateFlow(false)
    val showAddUdharCustomerDialog: StateFlow<Boolean> = _showAddUdharCustomerDialog.asStateFlow()

    private val _showRecordUdharEntryDialog = MutableStateFlow(false)
    val showRecordUdharEntryDialog: StateFlow<Boolean> = _showRecordUdharEntryDialog.asStateFlow()

    private val _activeUdharEntryType = MutableStateFlow(UdharType.GAVE)
    val activeUdharEntryType: StateFlow<UdharType> = _activeUdharEntryType.asStateFlow()

    private val _showAddCustomerDialog = MutableStateFlow(false)
    val showAddCustomerDialog: StateFlow<Boolean> = _showAddCustomerDialog.asStateFlow()

    private val _showPaymentDialog = MutableStateFlow(false)
    val showPaymentDialog: StateFlow<Boolean> = _showPaymentDialog.asStateFlow()

    private val _activePaymentType = MutableStateFlow(PaymentType.INTEREST_PAYMENT)
    val activePaymentType: StateFlow<PaymentType> = _activePaymentType.asStateFlow()

    private val _showConfirmAccrualDialog = MutableStateFlow(false)
    val showConfirmAccrualDialog: StateFlow<Boolean> = _showConfirmAccrualDialog.asStateFlow()

    fun setShowConfirmAccrualDialog(show: Boolean) {
        _showConfirmAccrualDialog.value = show
    }

    private val _showAddAccountDialog = MutableStateFlow(false)
    val showAddAccountDialog: StateFlow<Boolean> = _showAddAccountDialog.asStateFlow()

    private val _showAddTransactionDialog = MutableStateFlow(false)
    val showAddTransactionDialog: StateFlow<Boolean> = _showAddTransactionDialog.asStateFlow()

    private val _showAddBudgetDialog = MutableStateFlow(false)
    val showAddBudgetDialog: StateFlow<Boolean> = _showAddBudgetDialog.asStateFlow()

    private val _showAddGoalDialog = MutableStateFlow(false)
    val showAddGoalDialog: StateFlow<Boolean> = _showAddGoalDialog.asStateFlow()

    private val _selectedGoalForContribute = MutableStateFlow<Goal?>(null)
    val selectedGoalForContribute: StateFlow<Goal?> = _selectedGoalForContribute.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun showToast(msg: String) {
        _uiMessage.value = msg
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    // --- Udhar Khata Functions ---
    fun openAddUdharCustomerDialog() {
        _showAddUdharCustomerDialog.value = true
    }

    fun closeAddUdharCustomerDialog() {
        _showAddUdharCustomerDialog.value = false
    }

    fun addUdharCustomer(name: String, phone: String, address: String, notes: String): Boolean {
        if (name.isBlank()) {
            _uiMessage.value = "Kripya customer ka naam darj karein"
            return false
        }
        khataRepo.addUdharCustomer(name, phone, address, notes)
        _uiMessage.value = "Customer '$name' safalta se jud gaya!"
        _showAddUdharCustomerDialog.value = false
        return true
    }

    fun deleteUdharCustomer(customerId: String) {
        khataRepo.deleteUdharCustomer(customerId)
        if (_selectedUdharCustomerId.value == customerId) {
            _selectedUdharCustomerId.value = null
        }
        _uiMessage.value = "Customer aur uska hisab hata diya gaya"
    }

    fun openRecordUdharEntryDialog(type: UdharType) {
        _activeUdharEntryType.value = type
        _showRecordUdharEntryDialog.value = true
    }

    fun closeRecordUdharEntryDialog() {
        _showRecordUdharEntryDialog.value = false
    }

    fun recordUdharEntry(
        amount: Double,
        date: Long,
        note: String,
        billNumber: String
    ): Boolean {
        val custId = _selectedUdharCustomerId.value ?: return false
        val result = khataRepo.recordUdharEntry(
            customerId = custId,
            amount = amount,
            type = _activeUdharEntryType.value,
            date = date,
            note = note,
            billNumber = billNumber
        )
        return if (result.isSuccess) {
            val typeLabel = if (_activeUdharEntryType.value == UdharType.GAVE) "Udhar Diya" else "Payment Mila"
            _uiMessage.value = "$typeLabel: ₹$amount safalta se darj kiya gaya"
            _showRecordUdharEntryDialog.value = false
            true
        } else {
            _uiMessage.value = result.exceptionOrNull()?.message ?: "Error recording entry"
            false
        }
    }

    fun deleteUdharEntry(entryId: String) {
        khataRepo.deleteUdharEntry(entryId)
        _uiMessage.value = "Entry hata di gayi"
    }

    // --- Interest Loan Khata Functions ---
    fun selectCustomer(customerId: String?) {
        _selectedCustomerId.value = customerId
        val customer = customers.value.find { it.id == customerId }
        if (customer != null) {
            val suggestedStart = customer.lastCalculationDate ?: customer.loanStartDate
            _calcStartDate.value = suggestedStart
            _calcEndDate.value = System.currentTimeMillis()
            _calcRate.value = customer.monthlyInterestRate.toString()
            recalculatePreview(customer)
        }
    }

    fun setCalcStartDate(epochMs: Long) {
        _calcStartDate.value = epochMs
        selectedCustomer.value?.let { recalculatePreview(it) }
    }

    fun setCalcEndDate(epochMs: Long) {
        _calcEndDate.value = epochMs
        selectedCustomer.value?.let { recalculatePreview(it) }
    }

    fun setCalcRate(rateStr: String) {
        _calcRate.value = rateStr
        selectedCustomer.value?.let { recalculatePreview(it) }
    }

    fun recalculatePreview(customer: Customer? = selectedCustomer.value) {
        if (customer == null) return
        val rate = _calcRate.value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            ?: customer.monthlyInterestRate
        val existingAccruals = khataRepo.getCustomerAccruals(customer.id)

        val result = InterestCalculator.calculate(
            // Interest accrues on what is still owed, so principal repayments lower future interest.
            principal = customer.remainingPrincipal,
            monthlyRate = rate,
            startEpochMs = _calcStartDate.value,
            endEpochMs = _calcEndDate.value,
            existingAccruals = existingAccruals
        )
        _calculationPreview.value = result
    }

    fun openInterestCalculator(customerId: String) {
        selectCustomer(customerId)
    }

    fun confirmRecordAccrual(): Boolean {
        val customer = selectedCustomer.value ?: return false
        val preview = _calculationPreview.value ?: return false

        if (preview.isOverlapping) {
            _uiMessage.value = preview.overlapErrorMessage ?: "Cannot record: date range overlaps with existing interest."
            return false
        }

        if (preview.totalAccruedInterest <= 0.0 && preview.completedMonths == 0 && preview.partialDays == 0) {
            _uiMessage.value = "Kripya sahi tareekh chunein"
            return false
        }

        val accrual = InterestAccrual(
            customerId = customer.id,
            startDate = _calcStartDate.value,
            endDate = _calcEndDate.value,
            interestRate = preview.monthlyRate,
            principalBasis = preview.principalBasis,
            completedMonths = preview.completedMonths,
            partialDays = preview.partialDays,
            daysInPartialMonth = preview.daysInPartialMonth,
            monthlyInterestAmount = preview.monthlyInterestAmount,
            calculatedInterest = preview.totalAccruedInterest,
            calculationBreakdown = preview.formulaBreakdown
        )

        val result = khataRepo.recordInterestAccrual(accrual)
        if (result.isSuccess) {
            _uiMessage.value = "Byaaj ₹%,.2f safalta se darj kiya gaya!".format(accrual.calculatedInterest)
            _showConfirmAccrualDialog.value = false
            _calcStartDate.value = accrual.endDate
            _calcEndDate.value = System.currentTimeMillis()
            recalculatePreview()
            return true
        } else {
            _uiMessage.value = result.exceptionOrNull()?.message ?: "Error recording interest"
            return false
        }
    }

    fun openPaymentDialog(type: PaymentType) {
        _activePaymentType.value = type
        _showPaymentDialog.value = true
    }

    fun closePaymentDialog() {
        _showPaymentDialog.value = false
    }

    fun recordPayment(amount: Double, mode: String, notes: String): Boolean {
        val customer = selectedCustomer.value ?: return false
        val result = khataRepo.recordPayment(
            customerId = customer.id,
            paymentType = _activePaymentType.value,
            amount = amount,
            paymentMode = mode,
            notes = notes
        )
        return if (result.isSuccess) {
            _uiMessage.value = "${_activePaymentType.value.title} of ₹%,.2f recorded!".format(amount)
            _showPaymentDialog.value = false
            true
        } else {
            _uiMessage.value = result.exceptionOrNull()?.message ?: "Payment failed"
            false
        }
    }

    fun openAddCustomerDialog() {
        _showAddCustomerDialog.value = true
    }

    fun closeAddCustomerDialog() {
        _showAddCustomerDialog.value = false
    }

    fun addCustomer(
        name: String,
        phone: String,
        address: String,
        principal: Double,
        rate: Double,
        startDate: Long,
        notes: String
    ): Boolean {
        if (name.isBlank() || principal <= 0.0) {
            _uiMessage.value = "Kripya naam aur sahi mool dhan darj karein"
            return false
        }
        khataRepo.addCustomer(
            name = name,
            phone = phone,
            principalAmount = principal,
            monthlyInterestRate = rate,
            loanStartDate = startDate,
            notes = notes,
            address = address
        )
        _uiMessage.value = "Loan Borrower '$name' jud gaya!"
        _showAddCustomerDialog.value = false
        return true
    }

    fun deleteCustomer(id: String) {
        khataRepo.deleteCustomer(id)
        if (_selectedCustomerId.value == id) {
            _selectedCustomerId.value = null
        }
        _uiMessage.value = "Borrower aur loan hisab hata diya gaya"
    }

    // --- Personal Finance Functions ---
    fun openAddAccountDialog() { _showAddAccountDialog.value = true }
    fun closeAddAccountDialog() { _showAddAccountDialog.value = false }
    fun addAccount(name: String, type: AccountType, balance: Double, accNumber: String, colorHex: String): Boolean {
        if (name.isBlank()) { _uiMessage.value = "Account name is required"; return false }
        financeRepo.addAccount(name, type, balance, accNumber, colorHex)
        _uiMessage.value = "Account '$name' created!"
        _showAddAccountDialog.value = false
        return true
    }
    fun deleteAccount(id: String) {
        financeRepo.deleteAccount(id)
        _uiMessage.value = "Account deleted"
    }

    fun openAddTransactionDialog() { _showAddTransactionDialog.value = true }
    fun closeAddTransactionDialog() { _showAddTransactionDialog.value = false }
    fun addTransaction(
        accountId: String,
        targetAccountId: String?,
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        note: String
    ): Boolean {
        val result = financeRepo.addTransaction(accountId, targetAccountId, title, amount, type, category, note)
        if (result.isFailure) {
            _uiMessage.value = result.exceptionOrNull()?.message ?: "Enter valid title and amount"
            return false
        }
        _uiMessage.value = "Transaction recorded!"
        _showAddTransactionDialog.value = false
        return true
    }
    fun deleteTransaction(id: String) {
        financeRepo.deleteTransaction(id)
        _uiMessage.value = "Transaction deleted"
    }

    fun openAddBudgetDialog() { _showAddBudgetDialog.value = true }
    fun closeAddBudgetDialog() { _showAddBudgetDialog.value = false }
    fun addBudget(category: String, limit: Double, monthYear: String): Boolean {
        if (category.isBlank() || limit <= 0.0) {
            _uiMessage.value = "Enter valid category and limit"
            return false
        }
        financeRepo.addBudget(
            category, limit,
            monthYear.takeIf { it.matches(Regex("\\d{4}-\\d{2}")) } ?: java.time.YearMonth.now().toString()
        )
        _uiMessage.value = "Budget for '$category' set to ₹%,.2f".format(limit)
        _showAddBudgetDialog.value = false
        return true
    }
    fun deleteBudget(id: String) {
        financeRepo.deleteBudget(id)
        _uiMessage.value = "Budget removed"
    }

    fun openAddGoalDialog() { _showAddGoalDialog.value = true }
    fun closeAddGoalDialog() { _showAddGoalDialog.value = false }
    fun addGoal(name: String, target: Double, current: Double, deadlineDays: Int = 90, color: String = "#2563EB"): Boolean {
        if (name.isBlank() || target <= 0.0) {
            _uiMessage.value = "Enter valid goal name and target"
            return false
        }
        financeRepo.addGoal(name, target, current, deadlineDays, color)
        _uiMessage.value = "Savings Goal '$name' created!"
        _showAddGoalDialog.value = false
        return true
    }
    fun openContributeGoalDialog(goal: Goal) { _selectedGoalForContribute.value = goal }
    fun closeContributeGoalDialog() { _selectedGoalForContribute.value = null }
    fun contributeToGoal(goalId: String, amount: Double): Boolean {
        if (!financeRepo.contributeToGoal(goalId, amount)) {
            _uiMessage.value = "Enter a valid amount"
            return false
        }
        _uiMessage.value = "Saved ₹%,.2f towards goal!".format(amount)
        _selectedGoalForContribute.value = null
        return true
    }
    fun deleteGoal(id: String) {
        financeRepo.deleteGoal(id)
        _uiMessage.value = "Goal deleted"
    }

    // --- Cloud (Supabase) ---
    private val _isCloudBusy = MutableStateFlow(false)
    val isCloudBusy: StateFlow<Boolean> = _isCloudBusy.asStateFlow()

    fun authenticate(register: Boolean, supabaseUrl: String, anonKey: String, email: String, password: String) {
        if (_isCloudBusy.value) return
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                val result = if (register) supabase.signUp(supabaseUrl, anonKey, email, password)
                else supabase.signIn(supabaseUrl, anonKey, email, password)
                result
                    .onSuccess { session ->
                        khataRepo.updateUserSession(session)
                        _uiMessage.value = when {
                            session.isAuthenticated -> "Signed in as ${session.email}"
                            else -> "Account created. Check your e-mail to confirm it, then sign in."
                        }
                    }
                    .onFailure { _uiMessage.value = it.message ?: "Authentication failed" }
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    fun signOut() {
        khataRepo.updateUserSession(UserSession())
        _uiMessage.value = "Signed out"
    }

    fun syncNow() {
        if (_isCloudBusy.value) return
        val session = userSession.value
        if (!session.isAuthenticated) {
            _uiMessage.value = "Please sign in first"
            return
        }
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                syncManager.sync(session)
                    .onSuccess { report ->
                        khataRepo.updateUserSession(report.session)
                        _uiMessage.value = "Sync complete: ${report.uploaded} uploaded, ${report.downloaded} downloaded"
                    }
                    .onFailure { _uiMessage.value = "Sync failed: ${it.message ?: "unknown error"}" }
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    /** RFC 4180 compliant CSV (quotes escaped, formula-injection neutralised for spreadsheets). */
    fun generateCsvExport(): String {
        fun cell(value: Any?): String {
            var text = value?.toString().orEmpty().replace("\r", " ").replace("\n", " ")
            if (text.firstOrNull() in listOf('=', '+', '-', '@')) text = "'$text"
            return "\"" + text.replace("\"", "\"\"") + "\""
        }
        fun date(ms: Long) = InterestCalculator.millisToLocalDate(ms).toString()

        val sb = StringBuilder()
        sb.append("Domain,Date,Entity/Title,Type,Amount(INR),Details\n")

        val uCusts = udharCustomers.value
        udharEntries.value.forEach { entry ->
            val name = uCusts.find { it.id == entry.customerId }?.name ?: "Customer"
            val typeStr = if (entry.type == UdharType.GAVE) "Udhar Diya (Gave)" else "Payment Mila (Got)"
            sb.append(listOf("Udhar Khata", date(entry.date), name, typeStr, "%.2f".format(java.util.Locale.ENGLISH, entry.amount), "${entry.note} ${entry.billNumber}".trim()).joinToString(",") { cell(it) }).append('\n')
        }

        val bCusts = customers.value
        payments.value.forEach { pay ->
            val name = bCusts.find { it.id == pay.customerId }?.name ?: "Borrower"
            sb.append(listOf("Interest Loan", date(pay.paymentDate), name, pay.paymentType.title, "%.2f".format(java.util.Locale.ENGLISH, pay.amount), "${pay.paymentMode} - ${pay.notes}").joinToString(",") { cell(it) }).append('\n')
        }

        transactions.value.forEach { tx ->
            sb.append(listOf("Personal Finance", date(tx.dateTimestamp), tx.title, tx.type.name, "%.2f".format(java.util.Locale.ENGLISH, tx.amount), "${tx.category} - ${tx.note}").joinToString(",") { cell(it) }).append('\n')
        }

        return sb.toString()
    }

    companion object {
        /**
         * Production wiring: ledger data is persisted to the app's private storage and demo
         * records are only seeded in debug builds.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val dir = File(appContext.filesDir, "ledger")
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    KhataViewModel(
                        khataRepo = KhataRepository(
                            store = JsonFileStore(File(dir, "khata.json"), KhataSnapshot.serializer()),
                            scope = scope,
                            seedDemoData = BuildConfig.DEBUG
                        ),
                        financeRepo = FinanceRepository(
                            store = JsonFileStore(File(dir, "finance.json"), FinanceSnapshot.serializer()),
                            scope = scope,
                            seedDemoData = BuildConfig.DEBUG
                        )
                    )
                }
            }
        }
    }
}
