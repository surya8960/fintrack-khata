package com.example.fintrack.data.repository

import com.example.fintrack.data.local.FinanceSnapshot
import com.example.fintrack.data.local.LocalStore
import com.example.fintrack.data.local.PendingDelete
import com.example.fintrack.data.model.Account
import com.example.fintrack.data.model.AccountType
import com.example.fintrack.data.model.Budget
import com.example.fintrack.data.model.Goal
import com.example.fintrack.data.model.Transaction
import com.example.fintrack.data.model.TransactionType
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
import java.time.YearMonth
import java.util.UUID

/**
 * Source of truth for accounts, transactions, budgets and savings goals.
 * See [KhataRepository] for the meaning of the constructor parameters.
 */
class FinanceRepository(
    private val store: LocalStore<FinanceSnapshot>? = null,
    scope: CoroutineScope? = null,
    seedDemoData: Boolean = true
) {
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _pendingDeletes = MutableStateFlow<List<PendingDelete>>(emptyList())

    init {
        val restored = store?.load()
        when {
            restored != null -> applySnapshot(restored)
            seedDemoData -> seedInitialFinanceData()
        }
        if (store != null && scope != null) startPersistence(store, scope)
    }

    fun snapshot() = FinanceSnapshot(
        accounts = _accounts.value,
        transactions = _transactions.value,
        budgets = _budgets.value,
        goals = _goals.value,
        pendingDeletes = _pendingDeletes.value
    )

    fun applySnapshot(snapshot: FinanceSnapshot) {
        _accounts.value = snapshot.accounts
        _transactions.value = snapshot.transactions
        _budgets.value = snapshot.budgets
        _goals.value = snapshot.goals
        _pendingDeletes.value = snapshot.pendingDeletes
    }

    /** See [KhataRepository.mergeRemote]. */
    fun mergeRemote(remote: FinanceSnapshot) {
        val deleted = _pendingDeletes.value.map { it.id }.toSet()
        fun <T> List<T>.addMissing(incoming: List<T>, id: (T) -> String): List<T> {
            val known = map(id).toSet()
            return this + incoming.filter { id(it) !in known && id(it) !in deleted }
        }
        _accounts.update { it.addMissing(remote.accounts) { a -> a.id } }
        _transactions.update { it.addMissing(remote.transactions) { t -> t.id } }
        _budgets.update { it.addMissing(remote.budgets) { b -> b.id } }
        _goals.update { it.addMissing(remote.goals) { g -> g.id } }
    }

    fun pendingDeletes(): List<PendingDelete> = _pendingDeletes.value

    fun clearPendingDeletes(done: Collection<PendingDelete>) {
        _pendingDeletes.update { it - done.toSet() }
    }

    private fun markDeleted(table: String, id: String) {
        _pendingDeletes.update { it + PendingDelete(table, id) }
    }

    private fun startPersistence(store: LocalStore<FinanceSnapshot>, scope: CoroutineScope) {
        scope.launch {
            combine(_accounts, _transactions, _budgets, _goals, _pendingDeletes) { a, t, b, g, d ->
                FinanceSnapshot(a, t, b, g, d)
            }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { snap -> runCatching { store.save(snap) } }
        }
    }

    private fun seedInitialFinanceData() {
        val acc1Id = "acc-hdfc-01"
        val acc2Id = "acc-sbi-02"
        val acc3Id = "acc-cash-03"

        val accountList = listOf(
            Account(
                id = acc1Id,
                name = "HDFC Salary Account",
                type = AccountType.BANK,
                balance = 48500.0,
                accountNumber = "•••• 4892",
                colorHex = "#047857"
            ),
            Account(
                id = acc2Id,
                name = "SBI Savings",
                type = AccountType.BANK,
                balance = 120000.0,
                accountNumber = "•••• 7311",
                colorHex = "#1D4ED8"
            ),
            Account(
                id = acc3Id,
                name = "Shop Cash Box",
                type = AccountType.CASH,
                balance = 15200.0,
                accountNumber = "Cash in Hand",
                colorHex = "#D97706"
            )
        )

        val txList = listOf(
            Transaction(
                id = "tx-001",
                accountId = acc1Id,
                title = "Client Consulting Fee",
                amount = 35000.0,
                type = TransactionType.INCOME,
                category = "Business Income",
                note = "Direct NEFT transfer",
                dateTimestamp = System.currentTimeMillis() - 2L * 86400000L
            ),
            Transaction(
                id = "tx-002",
                accountId = acc1Id,
                title = "Office Space Rent",
                amount = 12000.0,
                type = TransactionType.EXPENSE,
                category = "Rent & Bills",
                note = "October maintenance included",
                dateTimestamp = System.currentTimeMillis() - 4L * 86400000L
            ),
            Transaction(
                id = "tx-003",
                accountId = acc3Id,
                title = "Stationery & Inventory",
                amount = 2400.0,
                type = TransactionType.EXPENSE,
                category = "Supplies",
                note = "Receipt #4092",
                dateTimestamp = System.currentTimeMillis() - 6L * 86400000L
            ),
            Transaction(
                id = "tx-004",
                accountId = acc1Id,
                targetAccountId = acc3Id,
                title = "ATM Cash Withdrawal",
                amount = 5000.0,
                type = TransactionType.TRANSFER,
                category = "Transfer",
                note = "Cash reserve replenishment",
                dateTimestamp = System.currentTimeMillis() - 8L * 86400000L
            )
        )

        val currentMonth = YearMonth.now().toString()
        val budgetList = listOf(
            Budget(
                id = "bgt-01",
                category = "Rent & Bills",
                monthlyLimit = 15000.0,
                monthYear = currentMonth
            ),
            Budget(
                id = "bgt-02",
                category = "Supplies",
                monthlyLimit = 10000.0,
                monthYear = currentMonth
            ),
            Budget(
                id = "bgt-03",
                category = "Travel & Food",
                monthlyLimit = 8000.0,
                monthYear = currentMonth
            )
        )

        val goalList = listOf(
            Goal(
                id = "gol-01",
                name = "New Shop Counter & Laptop",
                targetAmount = 75000.0,
                currentAmount = 45000.0,
                deadlineDays = 60,
                colorHex = "#047857"
            ),
            Goal(
                id = "gol-02",
                name = "Emergency Reserve Fund",
                targetAmount = 150000.0,
                currentAmount = 110000.0,
                deadlineDays = 120,
                colorHex = "#4F46E5"
            )
        )

        _accounts.value = accountList
        _transactions.value = txList
        _budgets.value = budgetList
        _goals.value = goalList
    }

    fun addAccount(name: String, type: AccountType, balance: Double, accountNumber: String, colorHex: String): Account {
        val newAccount = Account(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            balance = balance,
            accountNumber = accountNumber.trim(),
            colorHex = colorHex
        )
        _accounts.value = _accounts.value + newAccount
        return newAccount
    }

    fun updateAccount(account: Account) {
        _accounts.value = _accounts.value.map {
            if (it.id == account.id) account else it
        }
    }

    fun deleteAccount(accountId: String) {
        markDeleted("accounts", accountId)
        _accounts.value = _accounts.value.filter { it.id != accountId }
        _transactions.value = _transactions.value
            .filter { it.accountId != accountId }
            // Transfers INTO the deleted account stay as history on the source account;
            // only the dangling reference is cleared.
            .map { if (it.targetAccountId == accountId) it.copy(targetAccountId = null) else it }
    }

    fun addTransaction(
        accountId: String,
        targetAccountId: String?,
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        note: String
    ): Result<Transaction> {
        if (title.isBlank()) return Result.failure(IllegalArgumentException("Title is required"))
        if (amount <= 0.0 || !amount.isFinite()) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        if (_accounts.value.none { it.id == accountId }) {
            return Result.failure(IllegalArgumentException("Account not found"))
        }
        val target = if (type == TransactionType.TRANSFER) {
            when {
                targetAccountId == null ->
                    return Result.failure(IllegalArgumentException("Select a destination account"))
                targetAccountId == accountId ->
                    return Result.failure(IllegalArgumentException("Source and destination must differ"))
                _accounts.value.none { it.id == targetAccountId } ->
                    return Result.failure(IllegalArgumentException("Destination account not found"))
                else -> targetAccountId
            }
        } else null

        val newTx = Transaction(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            targetAccountId = target,
            title = title.trim(),
            amount = amount,
            type = type,
            category = category.trim(),
            note = note.trim(),
            dateTimestamp = System.currentTimeMillis()
        )
        _transactions.value = listOf(newTx) + _transactions.value

        // Adjust account balances automatically
        when (type) {
            TransactionType.INCOME -> adjustAccountBalance(accountId, amount)
            TransactionType.EXPENSE -> adjustAccountBalance(accountId, -amount)
            TransactionType.TRANSFER -> {
                adjustAccountBalance(accountId, -amount)
                adjustAccountBalance(target!!, amount)
            }
        }
        return Result.success(newTx)
    }

    fun deleteTransaction(txId: String) {
        val tx = _transactions.value.find { it.id == txId } ?: return
        markDeleted("transactions", txId)
        // Revert balance adjustment
        when (tx.type) {
            TransactionType.INCOME -> adjustAccountBalance(tx.accountId, -tx.amount)
            TransactionType.EXPENSE -> adjustAccountBalance(tx.accountId, tx.amount)
            TransactionType.TRANSFER -> {
                adjustAccountBalance(tx.accountId, tx.amount)
                if (tx.targetAccountId != null) {
                    adjustAccountBalance(tx.targetAccountId, -tx.amount)
                }
            }
        }
        _transactions.value = _transactions.value.filter { it.id != txId }
    }

    private fun adjustAccountBalance(accountId: String, delta: Double) {
        _accounts.value = _accounts.value.map {
            if (it.id == accountId) it.copy(balance = it.balance + delta) else it
        }
    }

    fun addBudget(
        category: String,
        monthlyLimit: Double,
        monthYear: String = YearMonth.now().toString()
    ): Budget {
        val newBudget = Budget(
            id = UUID.randomUUID().toString(),
            category = category.trim(),
            monthlyLimit = monthlyLimit,
            monthYear = monthYear
        )
        _budgets.value = _budgets.value + newBudget
        return newBudget
    }

    fun updateBudget(budget: Budget) {
        _budgets.value = _budgets.value.map {
            if (it.id == budget.id) budget else it
        }
    }

    fun deleteBudget(budgetId: String) {
        markDeleted("budgets", budgetId)
        _budgets.value = _budgets.value.filter { it.id != budgetId }
    }

    fun addGoal(name: String, targetAmount: Double, initialContribution: Double, deadlineDays: Int, colorHex: String): Goal {
        val newGoal = Goal(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            targetAmount = targetAmount,
            currentAmount = initialContribution,
            deadlineDays = deadlineDays,
            colorHex = colorHex
        )
        _goals.value = _goals.value + newGoal
        return newGoal
    }

    fun contributeToGoal(goalId: String, amount: Double): Boolean {
        if (amount <= 0.0 || !amount.isFinite() || _goals.value.none { it.id == goalId }) return false
        _goals.value = _goals.value.map {
            if (it.id == goalId) it.copy(currentAmount = it.currentAmount + amount) else it
        }
        return true
    }

    fun deleteGoal(goalId: String) {
        markDeleted("goals", goalId)
        _goals.value = _goals.value.filter { it.id != goalId }
    }
}
