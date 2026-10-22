package com.example.fintrack.data.repository

import com.example.fintrack.data.local.KhataSnapshot
import com.example.fintrack.data.local.LocalStore
import com.example.fintrack.data.local.PendingDelete
import com.example.fintrack.data.model.Customer
import com.example.fintrack.data.model.InterestAccrual
import com.example.fintrack.data.model.Payment
import com.example.fintrack.data.model.PaymentType
import com.example.fintrack.data.model.UdharCustomer
import com.example.fintrack.data.model.UdharEntry
import com.example.fintrack.data.model.UdharType
import com.example.fintrack.data.model.UserSession
import com.example.fintrack.domain.calculator.InterestCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Source of truth for the udhar ledger and interest loans.
 *
 * @param store optional persistence; when present the previous session is restored on start and
 *   every change is written back (off the main thread).
 * @param scope scope that owns the background persistence job (use the ViewModel scope).
 * @param seedDemoData when true and nothing is stored yet, demo records are inserted
 *   (intended for debug builds only).
 */
class KhataRepository(
    private val store: LocalStore<KhataSnapshot>? = null,
    scope: CoroutineScope? = null,
    seedDemoData: Boolean = true
) {
    // --- Udhar Khata (Customer Credit/Debit Ledger) State ---
    private val _udharCustomers = MutableStateFlow<List<UdharCustomer>>(emptyList())
    val udharCustomers: StateFlow<List<UdharCustomer>> = _udharCustomers.asStateFlow()

    private val _udharEntries = MutableStateFlow<List<UdharEntry>>(emptyList())
    val udharEntries: StateFlow<List<UdharEntry>> = _udharEntries.asStateFlow()

    // --- Interest Loan Khata (Borrower Money Lending) State ---
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _interestAccruals = MutableStateFlow<List<InterestAccrual>>(emptyList())
    val interestAccruals: StateFlow<List<InterestAccrual>> = _interestAccruals.asStateFlow()

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    private val _pendingDeletes = MutableStateFlow<List<PendingDelete>>(emptyList())

    private val _userSession = MutableStateFlow(UserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        val restored = store?.load()
        when {
            restored != null -> applySnapshot(restored)
            seedDemoData -> {
                seedInitialUdharData()
                seedInitialLoanData()
            }
        }
        if (store != null && scope != null) startPersistence(store, scope)
    }

    fun snapshot() = KhataSnapshot(
        udharCustomers = _udharCustomers.value,
        udharEntries = _udharEntries.value,
        customers = _customers.value,
        interestAccruals = _interestAccruals.value,
        payments = _payments.value,
        pendingDeletes = _pendingDeletes.value
    )

    /** Replaces all in-memory data (used when restoring from disk or after a cloud pull). */
    fun applySnapshot(snapshot: KhataSnapshot) {
        _udharCustomers.value = snapshot.udharCustomers
        _udharEntries.value = snapshot.udharEntries
        _customers.value = snapshot.customers
        _interestAccruals.value = snapshot.interestAccruals
        _payments.value = snapshot.payments
        _pendingDeletes.value = snapshot.pendingDeletes
    }

    /**
     * Merges rows downloaded from the cloud into local state. Local rows always win on id clashes
     * and rows that were deleted locally (but not yet replayed on the cloud) are not resurrected.
     */
    fun mergeRemote(remote: KhataSnapshot) {
        val deleted = _pendingDeletes.value.map { it.id }.toSet()
        fun <T> List<T>.addMissing(incoming: List<T>, id: (T) -> String): List<T> {
            val known = map(id).toSet()
            return this + incoming.filter { id(it) !in known && id(it) !in deleted }
        }
        _udharCustomers.update { it.addMissing(remote.udharCustomers) { c -> c.id } }
        _udharEntries.update { it.addMissing(remote.udharEntries) { e -> e.id } }
        _customers.update { it.addMissing(remote.customers) { c -> c.id } }
        _interestAccruals.update { it.addMissing(remote.interestAccruals) { a -> a.id } }
        _payments.update { it.addMissing(remote.payments) { p -> p.id } }
    }

    fun pendingDeletes(): List<PendingDelete> = _pendingDeletes.value

    fun clearPendingDeletes(done: Collection<PendingDelete>) {
        _pendingDeletes.update { it - done.toSet() }
    }

    private fun markDeleted(table: String, id: String) {
        _pendingDeletes.update { it + PendingDelete(table, id) }
    }

    private fun startPersistence(store: LocalStore<KhataSnapshot>, scope: CoroutineScope) {
        scope.launch {
            combine(
                _udharCustomers, _udharEntries, _customers, _interestAccruals, _payments, _pendingDeletes
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                KhataSnapshot(
                    values[0] as List<UdharCustomer>,
                    values[1] as List<UdharEntry>,
                    values[2] as List<Customer>,
                    values[3] as List<InterestAccrual>,
                    values[4] as List<Payment>,
                    values[5] as List<PendingDelete>
                )
            }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { snap -> runCatching { store.save(snap) } }
        }
    }

    private fun seedInitialUdharData() {
        val now = System.currentTimeMillis()
        val dayMs = 86400000L

        val uc1 = UdharCustomer(
            id = "uc-001",
            name = "Ramesh General Store",
            phone = "+91 98765 12345",
            address = "Main Market, Shop #12",
            notes = "Daily grocery credit customer"
        )
        val uc2 = UdharCustomer(
            id = "uc-002",
            name = "Priya Medical Store",
            phone = "+91 94567 89012",
            address = "Station Road, Near Civil Hospital",
            notes = "Wholesale medicine orders"
        )
        val uc3 = UdharCustomer(
            id = "uc-003",
            name = "Amit Sharma",
            phone = "+91 91234 56780",
            address = "Sector 4, Flat 102",
            notes = "Regular neighborhood customer"
        )

        val entries = listOf(
            UdharEntry(
                id = "ue-001",
                customerId = "uc-001",
                amount = 4500.0,
                type = UdharType.GAVE,
                date = now - (5 * dayMs),
                note = "Rice & Cooking Oil sacks",
                billNumber = "BILL-809"
            ),
            UdharEntry(
                id = "ue-002",
                customerId = "uc-001",
                amount = 2000.0,
                type = UdharType.GOT,
                date = now - (2 * dayMs),
                note = "Cash payment received",
                billNumber = ""
            ),
            UdharEntry(
                id = "ue-003",
                customerId = "uc-002",
                amount = 8200.0,
                type = UdharType.GAVE,
                date = now - (8 * dayMs),
                note = "Surgical items carton",
                billNumber = "INV-412"
            ),
            UdharEntry(
                id = "ue-004",
                customerId = "uc-002",
                amount = 8200.0,
                type = UdharType.GOT,
                date = now - (1 * dayMs),
                note = "Full payment via PhonePe",
                billNumber = "UPI-771"
            ),
            UdharEntry(
                id = "ue-005",
                customerId = "uc-003",
                amount = 1850.0,
                type = UdharType.GAVE,
                date = now - (3 * dayMs),
                note = "Monthly household rations",
                billNumber = "BILL-930"
            )
        )

        _udharCustomers.value = listOf(uc1, uc2, uc3)
        _udharEntries.value = entries
    }

    private fun seedInitialLoanData() {
        val now = LocalDate.now()
        val oct5 = LocalDate.of(now.year, 10, 5)
        val oct5Ms = InterestCalculator.localDateToMillis(oct5)
        val nov5 = oct5.plusMonths(1)
        val nov5Ms = InterestCalculator.localDateToMillis(nov5)

        val customer1Id = "c1111111-1111-1111-1111-111111111111"
        val customer1 = Customer(
            id = customer1Id,
            name = "Rajesh Kumar",
            phone = "+91 98765 43210",
            address = "Gali No. 3, Gandhi Nagar",
            principalAmount = 10000.0,
            monthlyInterestRate = 5.0,
            loanStartDate = oct5Ms,
            totalAccruedInterest = 500.0,
            totalInterestPaid = 0.0,
            totalPrincipalRepaid = 0.0,
            lastCalculationDate = nov5Ms,
            status = "ACTIVE",
            notes = "Shop expansion loan. 5% monthly rate as agreed."
        )

        val accrual1 = InterestAccrual(
            id = "acc11111-1111-1111-1111-111111111111",
            customerId = customer1Id,
            startDate = oct5Ms,
            endDate = nov5Ms,
            interestRate = 5.0,
            principalBasis = 10000.0,
            completedMonths = 1,
            partialDays = 0,
            daysInPartialMonth = 30,
            monthlyInterestAmount = 500.0,
            calculatedInterest = 500.0,
            calculationBreakdown = "• Principal basis: ₹10,000.00\n• Monthly rate: 5.00% (₹500.00 / month)\n• Completed monthly periods: 1 month(s) = ₹500.00\n• Partial period: None\n• Total Accrued Simple Interest: ₹500.00"
        )

        val customer2Id = "c2222222-2222-2222-2222-222222222222"
        val sep15 = LocalDate.of(now.year, 9, 15)
        val sep15Ms = InterestCalculator.localDateToMillis(sep15)
        val customer2 = Customer(
            id = customer2Id,
            name = "Sunil Verma",
            phone = "+91 91234 56789",
            address = "Rani Bagh, Old Market",
            principalAmount = 25000.0,
            monthlyInterestRate = 4.0,
            loanStartDate = sep15Ms,
            totalAccruedInterest = 1000.0,
            totalInterestPaid = 1000.0, // Fully paid interest
            totalPrincipalRepaid = 5000.0, // Partial principal repaid
            lastCalculationDate = InterestCalculator.localDateToMillis(sep15.plusMonths(1)),
            status = "ACTIVE",
            notes = "Wholesale inventory credit."
        )

        val payment1 = Payment(
            id = "pay11111-1111-1111-1111-111111111111",
            customerId = customer2Id,
            paymentType = PaymentType.INTEREST_PAYMENT,
            amount = 1000.0,
            paymentDate = InterestCalculator.localDateToMillis(sep15.plusMonths(1)),
            paymentMode = "UPI",
            notes = "Interest paid on time for 1st month"
        )

        val payment2 = Payment(
            id = "pay22222-2222-2222-2222-222222222222",
            customerId = customer2Id,
            paymentType = PaymentType.PRINCIPAL_REPAYMENT,
            amount = 5000.0,
            paymentDate = InterestCalculator.localDateToMillis(sep15.plusMonths(1)),
            paymentMode = "CASH",
            notes = "Principal reduction installment"
        )

        _customers.value = listOf(customer1, customer2)
        _interestAccruals.value = listOf(accrual1)
        _payments.value = listOf(payment1, payment2)
    }

    // --- Udhar Khata Actions ---

    fun addUdharCustomer(
        name: String,
        phone: String,
        address: String,
        notes: String
    ): UdharCustomer {
        val newCustomer = UdharCustomer(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim()
        )
        _udharCustomers.value = _udharCustomers.value + newCustomer
        return newCustomer
    }

    fun updateUdharCustomer(customer: UdharCustomer) {
        _udharCustomers.value = _udharCustomers.value.map {
            if (it.id == customer.id) customer else it
        }
    }

    fun deleteUdharCustomer(customerId: String) {
        markDeleted("udhar_customers", customerId)
        _udharCustomers.value = _udharCustomers.value.filter { it.id != customerId }
        _udharEntries.value = _udharEntries.value.filter { it.customerId != customerId }
    }

    fun recordUdharEntry(
        customerId: String,
        amount: Double,
        type: UdharType,
        date: Long,
        note: String,
        billNumber: String
    ): Result<UdharEntry> {
        if (_udharCustomers.value.none { it.id == customerId }) {
            return Result.failure(Exception("Customer not found"))
        }

        if (amount <= 0.0 || !amount.isFinite()) {
            return Result.failure(Exception("Amount must be greater than zero"))
        }

        val entry = UdharEntry(
            id = UUID.randomUUID().toString(),
            customerId = customerId,
            amount = amount,
            type = type,
            date = date,
            note = note.trim(),
            billNumber = billNumber.trim()
        )
        _udharEntries.value = _udharEntries.value + entry
        return Result.success(entry)
    }

    fun deleteUdharEntry(entryId: String) {
        markDeleted("udhar_entries", entryId)
        _udharEntries.value = _udharEntries.value.filter { it.id != entryId }
    }

    fun getUdharCustomerEntries(customerId: String): List<UdharEntry> {
        return _udharEntries.value
            .filter { it.customerId == customerId }
            .sortedByDescending { it.date }
    }

    // --- Interest Loan Khata Actions ---

    fun addCustomer(
        name: String,
        phone: String,
        principalAmount: Double,
        monthlyInterestRate: Double,
        loanStartDate: Long,
        notes: String,
        address: String = "",
        dueDate: Long? = null
    ): Customer {
        require(name.isNotBlank()) { "Name is required" }
        require(principalAmount > 0.0 && principalAmount.isFinite()) { "Principal must be greater than zero" }
        require(monthlyInterestRate >= 0.0 && monthlyInterestRate.isFinite()) { "Rate cannot be negative" }
        val newCustomer = Customer(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim(),
            principalAmount = principalAmount,
            monthlyInterestRate = monthlyInterestRate,
            loanStartDate = loanStartDate,
            dueDate = dueDate,
            totalAccruedInterest = 0.0,
            totalInterestPaid = 0.0,
            totalPrincipalRepaid = 0.0,
            notes = notes.trim()
        )
        _customers.value = _customers.value + newCustomer
        return newCustomer
    }

    fun updateCustomer(customer: Customer) {
        _customers.value = _customers.value.map {
            if (it.id == customer.id) customer else it
        }
    }

    fun deleteCustomer(customerId: String) {
        markDeleted("khata_customers", customerId)
        _customers.value = _customers.value.filter { it.id != customerId }
        _interestAccruals.value = _interestAccruals.value.filter { it.customerId != customerId }
        _payments.value = _payments.value.filter { it.customerId != customerId }
    }

    fun recordInterestAccrual(accrual: InterestAccrual): Result<Unit> {
        val customer = _customers.value.find { it.id == accrual.customerId }
            ?: return Result.failure(Exception("Customer not found"))

        if (accrual.calculatedInterest < 0.0 || !accrual.calculatedInterest.isFinite()) {
            return Result.failure(Exception("Invalid interest amount"))
        }

        val overlaps = getCustomerAccruals(accrual.customerId).any {
            maxOf(it.startDate, accrual.startDate) < minOf(it.endDate, accrual.endDate)
        }
        if (overlaps) {
            return Result.failure(Exception("Interest is already recorded for an overlapping period"))
        }

        _interestAccruals.value = _interestAccruals.value + accrual

        val updatedCustomer = customer.copy(
            totalAccruedInterest = customer.totalAccruedInterest + accrual.calculatedInterest,
            lastCalculationDate = accrual.endDate
        )
        updateCustomer(updatedCustomer)
        return Result.success(Unit)
    }

    fun recordPayment(
        customerId: String,
        paymentType: PaymentType,
        amount: Double,
        paymentMode: String,
        notes: String
    ): Result<Payment> {
        val customer = _customers.value.find { it.id == customerId }
            ?: return Result.failure(Exception("Customer not found"))

        if (amount <= 0.0 || !amount.isFinite()) {
            return Result.failure(Exception("Payment amount must be greater than zero"))
        }

        // Never allow a repayment larger than what is actually outstanding; otherwise the
        // ledger would silently absorb the overpayment (balances are clamped at zero).
        val outstanding = when (paymentType) {
            PaymentType.INTEREST_PAYMENT -> customer.outstandingInterest
            PaymentType.PRINCIPAL_REPAYMENT -> customer.remainingPrincipal
        }
        if (amount > outstanding + MONEY_EPSILON) {
            val label = if (paymentType == PaymentType.INTEREST_PAYMENT) "outstanding interest" else "remaining principal"
            return Result.failure(
                Exception("Amount exceeds %s (₹%,.2f)".format(java.util.Locale.ENGLISH, label, outstanding))
            )
        }

        val payment = Payment(
            customerId = customerId,
            paymentType = paymentType,
            amount = amount,
            paymentDate = System.currentTimeMillis(),
            paymentMode = paymentMode,
            notes = notes
        )

        _payments.value = _payments.value + payment

        val updatedCustomer = when (paymentType) {
            PaymentType.INTEREST_PAYMENT -> {
                customer.copy(totalInterestPaid = customer.totalInterestPaid + amount)
            }
            PaymentType.PRINCIPAL_REPAYMENT -> {
                customer.copy(totalPrincipalRepaid = customer.totalPrincipalRepaid + amount)
            }
        }
        updateCustomer(updatedCustomer)
        return Result.success(payment)
    }

    fun getCustomerAccruals(customerId: String): List<InterestAccrual> {
        return _interestAccruals.value
            .filter { it.customerId == customerId }
            .sortedByDescending { it.endDate }
    }

    fun getCustomerPayments(customerId: String): List<Payment> {
        return _payments.value
            .filter { it.customerId == customerId }
            .sortedByDescending { it.paymentDate }
    }

    fun updateUserSession(session: UserSession) {
        _userSession.value = session
    }

    private companion object {
        const val MONEY_EPSILON = 0.005
    }
}
