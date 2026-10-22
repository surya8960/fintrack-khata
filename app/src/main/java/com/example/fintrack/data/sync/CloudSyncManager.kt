package com.example.fintrack.data.sync

import com.example.fintrack.data.local.FinanceSnapshot
import com.example.fintrack.data.local.KhataSnapshot
import com.example.fintrack.data.model.Account
import com.example.fintrack.data.model.Budget
import com.example.fintrack.data.model.Customer
import com.example.fintrack.data.model.Goal
import com.example.fintrack.data.model.InterestAccrual
import com.example.fintrack.data.model.Payment
import com.example.fintrack.data.model.Transaction
import com.example.fintrack.data.model.UdharCustomer
import com.example.fintrack.data.model.UdharEntry
import com.example.fintrack.data.model.UserSession
import com.example.fintrack.data.repository.FinanceRepository
import com.example.fintrack.data.repository.KhataRepository
import com.example.fintrack.data.supabase.SupabaseException
import com.example.fintrack.data.supabase.SupabaseService
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class SyncReport(val session: UserSession, val uploaded: Int, val downloaded: Int, val deleted: Int)

/**
 * Two-way sync between the local ledger and the user's Supabase project.
 *
 * Strategy (documented in the README):
 *  1. replay local deletions on the cloud,
 *  2. upsert every local row (parents before children because of foreign keys),
 *  3. download rows that exist only in the cloud and merge them in.
 *
 * Local data always wins on conflicts; column names are mapped camelCase -> snake_case.
 */
class CloudSyncManager(
    private val service: SupabaseService,
    private val khata: KhataRepository,
    private val finance: FinanceRepository
) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    /** One cloud table: its name plus a function that encodes the local rows as JSON. */
    private class Table(val name: String, val encode: (KhataSnapshot, FinanceSnapshot) -> JsonArray)

    private fun <T> table(
        name: String,
        serializer: KSerializer<T>,
        read: (KhataSnapshot, FinanceSnapshot) -> List<T>
    ) = Table(name) { k, f ->
        json.encodeToJsonElement(ListSerializer(serializer), read(k, f)) as JsonArray
    }

    // Parents first: foreign keys require customers/accounts to exist before their children.
    private val tables: List<Table> = listOf(
        table("khata_customers", Customer.serializer()) { k, _ -> k.customers },
        table("interest_accruals", InterestAccrual.serializer()) { k, _ -> k.interestAccruals },
        table("loan_payments", Payment.serializer()) { k, _ -> k.payments },
        table("udhar_customers", UdharCustomer.serializer()) { k, _ -> k.udharCustomers },
        table("udhar_entries", UdharEntry.serializer()) { k, _ -> k.udharEntries },
        table("accounts", Account.serializer()) { _, f -> f.accounts },
        table("transactions", Transaction.serializer()) { _, f -> f.transactions },
        table("budgets", Budget.serializer()) { _, f -> f.budgets },
        table("goals", Goal.serializer()) { _, f -> f.goals }
    )

    suspend fun sync(initial: UserSession): Result<SyncReport> = runCatching {
        require(initial.isAuthenticated && initial.token != null && initial.userId != null) { "Please sign in first" }

        var session = initial
        // Runs [block], refreshing the access token once if it expired mid-sync.
        suspend fun <R> authed(block: suspend (UserSession) -> R): R = try {
            block(session)
        } catch (e: SupabaseException) {
            if (e.status != 401) throw e
            session = service.refresh(session).getOrThrow().copy(email = session.email)
            block(session)
        }

        val localK = khata.snapshot()
        val localF = finance.snapshot()

        // 1. Deletions
        val pending = khata.pendingDeletes() + finance.pendingDeletes()
        pending.forEach { d -> authed { service.deleteById(it, d.table, d.id) } }
        khata.clearPendingDeletes(pending)
        finance.clearPendingDeletes(pending)

        // 2. Upload
        var uploaded = 0
        val userId = session.userId!!
        for (table in tables) {
            val rows = table.encode(localK, localF).map { (it as JsonObject).withUser(userId) }
            rows.chunked(UPLOAD_CHUNK).forEach { chunk ->
                authed { service.upsert(it, table.name, JsonArray(chunk)) }
            }
            uploaded += rows.size
        }

        // 3. Download + merge
        val remoteRows = HashMap<String, JsonArray>()
        tables.forEach { table -> remoteRows[table.name] = authed { service.fetchAll(it, table.name) } }
        val remoteK = KhataSnapshot(
            customers = decode("khata_customers", Customer.serializer(), remoteRows),
            interestAccruals = decode("interest_accruals", InterestAccrual.serializer(), remoteRows),
            payments = decode("loan_payments", Payment.serializer(), remoteRows),
            udharCustomers = decode("udhar_customers", UdharCustomer.serializer(), remoteRows),
            udharEntries = decode("udhar_entries", UdharEntry.serializer(), remoteRows)
        )
        val remoteF = FinanceSnapshot(
            accounts = decode("accounts", Account.serializer(), remoteRows),
            transactions = decode("transactions", Transaction.serializer(), remoteRows),
            budgets = decode("budgets", Budget.serializer(), remoteRows),
            goals = decode("goals", Goal.serializer(), remoteRows)
        )
        val before = khata.snapshot().total() + finance.snapshot().total()
        khata.mergeRemote(remoteK)
        finance.mergeRemote(remoteF)
        val downloaded = khata.snapshot().total() + finance.snapshot().total() - before

        SyncReport(
            session = session.copy(lastSyncTime = System.currentTimeMillis()),
            uploaded = uploaded,
            downloaded = downloaded,
            deleted = pending.size
        )
    }

    private fun <T> decode(name: String, serializer: KSerializer<T>, rows: Map<String, JsonArray>): List<T> =
        rows[name]?.let { json.decodeFromJsonElement(ListSerializer(serializer), it) } ?: emptyList()

    private fun JsonObject.withUser(userId: String): JsonElement =
        JsonObject(this + ("user_id" to JsonPrimitive(userId)))

    private fun KhataSnapshot.total() =
        customers.size + interestAccruals.size + payments.size + udharCustomers.size + udharEntries.size

    private fun FinanceSnapshot.total() = accounts.size + transactions.size + budgets.size + goals.size

    private companion object {
        const val UPLOAD_CHUNK = 500
    }
}
