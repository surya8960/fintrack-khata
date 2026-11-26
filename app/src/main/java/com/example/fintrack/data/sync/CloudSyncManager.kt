package com.example.fintrack.data.sync

import com.example.fintrack.data.local.FinanceSnapshot
import com.example.fintrack.data.local.KhataSnapshot
import com.example.fintrack.data.local.PendingDelete
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SyncReport(
    val session: UserSession,
    val uploaded: Int,
    val downloaded: Int,
    val deleted: Int,
    /** Rows the server rejected (constraint violations). They stay local and sync continues. */
    val skipped: Int = 0
)

/**
 * Two-way sync between the local ledger and the user's Supabase project.
 *
 * Strategy (documented in the README):
 *  1. PULL rows + deletion tombstones from the cloud,
 *  2. MERGE by id: newest `updatedAt` wins; tombstoned ids are removed and never come back,
 *  3. PUSH new tombstones, then only the rows that are new/newer than the cloud copy
 *     (parents before children because of foreign keys).
 *
 * Every record has a client-generated UUID `id`, so the same record can never be created twice.
 * Column names are mapped camelCase -> snake_case.
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
        val userId = initial.userId!!

        // 1. PULL first: rows + tombstones from the cloud.
        val remoteRows = HashMap<String, JsonArray>()
        tables.forEach { table -> remoteRows[table.name] = authed { service.fetchAll(it, table.name) } }
        val remoteTombstones = authed { service.fetchAll(it, TOMBSTONES) }.map { row ->
            val o = row as JsonObject
            PendingDelete(
                table = o["table_name"]?.jsonPrimitive?.content.orEmpty(),
                id = o["id"]?.jsonPrimitive?.content.orEmpty(),
                deletedAt = o["deleted_at"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }.filter { it.id.isNotEmpty() }

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

        // 2. MERGE: newest updatedAt wins, tombstoned ids are removed and never resurrected.
        val before = khata.snapshot().total() + finance.snapshot().total()
        khata.mergeRemote(remoteK, remoteTombstones)
        finance.mergeRemote(remoteF, remoteTombstones)
        val downloaded = (khata.snapshot().total() + finance.snapshot().total() - before).coerceAtLeast(0)

        val localK = khata.snapshot()
        val localF = finance.snapshot()

        // 3. PUSH tombstones (and remove the cloud rows) for deletions the cloud does not know yet.
        val knownRemoteDead = remoteTombstones.map { it.id }.toSet()
        val tombstones = (localK.pendingDeletes + localF.pendingDeletes).distinctBy { it.id }
        val newTombstones = tombstones.filter { it.id !in knownRemoteDead }
        if (newTombstones.isNotEmpty()) {
            val rows = newTombstones.map {
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(it.id),
                        "table_name" to JsonPrimitive(it.table),
                        "deleted_at" to JsonPrimitive(it.deletedAt),
                        "user_id" to JsonPrimitive(userId)
                    )
                )
            }
            rows.chunked(UPLOAD_CHUNK).forEach { chunk ->
                authed { service.upsert(it, TOMBSTONES, JsonArray(chunk), onConflict = "user_id,id") }
            }
            newTombstones.forEach { d -> authed { service.deleteById(it, d.table, d.id) } }
        }

        // 4. PUSH rows that are new or newer than the cloud copy (parents before children).
        var uploaded = 0
        var skipped = 0
        for (table in tables) {
            val remoteStamp: Map<String, Long> = (remoteRows[table.name] ?: JsonArray(emptyList())).associate { r ->
                val o = r as JsonObject
                o["id"]?.jsonPrimitive?.content.orEmpty() to (o["updated_at"]?.jsonPrimitive?.longOrNull ?: 0L)
            }
            val rows = table.encode(localK, localF).map { (it as JsonObject).withUser(userId) }.filter { row ->
                val o = row as JsonObject
                val id = o["id"]?.jsonPrimitive?.content.orEmpty()
                val mine = o["updated_at"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
                val theirs = remoteStamp[id]
                theirs == null || mine > theirs
            }
            rows.chunked(UPLOAD_CHUNK).forEach { chunk ->
                try {
                    authed { service.upsert(it, table.name, JsonArray(chunk)) }
                    uploaded += chunk.size
                } catch (e: SupabaseException) {
                    // 400/409/422 = the server rejected the payload (e.g. a CHECK constraint).
                    // One bad row must not block the whole ledger, so retry row by row.
                    if (e.status !in ROW_ERROR_STATUSES) throw e
                    chunk.forEach { row ->
                        try {
                            authed { service.upsert(it, table.name, JsonArray(listOf(row))) }
                            uploaded++
                        } catch (single: SupabaseException) {
                            if (single.status !in ROW_ERROR_STATUSES) throw single
                            skipped++
                        }
                    }
                }
            }
        }

        SyncReport(
            session = session.copy(lastSyncTime = System.currentTimeMillis()),
            uploaded = uploaded,
            downloaded = downloaded,
            deleted = newTombstones.size,
            skipped = skipped
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
        const val TOMBSTONES = "sync_tombstones"
        val ROW_ERROR_STATUSES = setOf(400, 409, 422)
    }
}
