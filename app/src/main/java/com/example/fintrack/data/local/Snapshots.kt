package com.example.fintrack.data.local

import com.example.fintrack.data.model.Account
import com.example.fintrack.data.model.Budget
import com.example.fintrack.data.model.Customer
import com.example.fintrack.data.model.Goal
import com.example.fintrack.data.model.InterestAccrual
import com.example.fintrack.data.model.Payment
import com.example.fintrack.data.model.Transaction
import com.example.fintrack.data.model.UdharCustomer
import com.example.fintrack.data.model.UdharEntry
import kotlinx.serialization.Serializable

/**
 * A deletion tombstone. Tombstones are kept permanently (never cleared) and synced to the cloud
 * (`sync_tombstones`), so every device learns about a delete and can never re-upload the old row.
 */
@Serializable
data class PendingDelete(
    val table: String,
    val id: String,
    val deletedAt: Long = System.currentTimeMillis()
)

/** Everything the Khata (udhar + interest loans) repository persists. */
@Serializable
data class KhataSnapshot(
    val udharCustomers: List<UdharCustomer> = emptyList(),
    val udharEntries: List<UdharEntry> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val interestAccruals: List<InterestAccrual> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val pendingDeletes: List<PendingDelete> = emptyList(),
    /** Cloud account (user id) this local ledger belongs to; null = created before any login. */
    val ownerUserId: String? = null
)

/** Everything the personal-finance repository persists. */
@Serializable
data class FinanceSnapshot(
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val pendingDeletes: List<PendingDelete> = emptyList()
)
