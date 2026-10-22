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

/** A local deletion that still has to be replayed on the cloud database. */
@Serializable
data class PendingDelete(val table: String, val id: String)

/** Everything the Khata (udhar + interest loans) repository persists. */
@Serializable
data class KhataSnapshot(
    val udharCustomers: List<UdharCustomer> = emptyList(),
    val udharEntries: List<UdharEntry> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val interestAccruals: List<InterestAccrual> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val pendingDeletes: List<PendingDelete> = emptyList()
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
