package com.example.fintrack.data.sync

import com.example.fintrack.data.local.PendingDelete

/**
 * Sync rules (shared by Khata and Finance repositories):
 *  - every row has a unique UUID `id` and an `updatedAt` timestamp,
 *  - a deleted row leaves a tombstone (id + deletedAt) that is synced everywhere,
 *  - for the same id the NEWEST `updatedAt` wins; a tombstoned id never comes back.
 */
internal object MergeUtil {

    /** Fixed-id sample rows created by very old versions. They must never sync or come back. */
    val DEMO_IDS: Set<String> = setOf(
        "uc-001", "uc-002", "uc-003",
        "ue-001", "ue-002", "ue-003", "ue-004", "ue-005",
        "c1111111-1111-1111-1111-111111111111", "c2222222-2222-2222-2222-222222222222",
        "acc11111-1111-1111-1111-111111111111",
        "pay11111-1111-1111-1111-111111111111", "pay22222-2222-2222-2222-222222222222",
        "acc-hdfc-01", "acc-sbi-02", "acc-cash-03",
        "tx-001", "tx-002", "tx-003", "tx-004",
        "bgt-01", "bgt-02", "bgt-03", "gol-01", "gol-02"
    )

    /** Union of tombstones; one per id (the earliest deletion time is kept). */
    fun mergeTombstones(a: List<PendingDelete>, b: List<PendingDelete>): List<PendingDelete> =
        (a + b).groupBy { it.id }.map { (_, v) -> v.minBy { it.deletedAt } }

    /**
     * Merges [remote] into [local]: drops dead ids, removes duplicate ids, and for an id present
     * on both sides keeps the row with the larger `updatedAt` (local on a tie).
     */
    fun <T> mergeById(
        local: List<T>,
        remote: List<T>,
        dead: Set<String>,
        id: (T) -> String,
        updatedAt: (T) -> Long
    ): List<T> {
        val result = LinkedHashMap<String, T>()
        for (row in local) {
            val key = id(row)
            if (key in dead) continue
            val existing = result[key]
            if (existing == null || updatedAt(row) > updatedAt(existing)) result[key] = row
        }
        for (row in remote) {
            val key = id(row)
            if (key in dead) continue
            val mine = result[key]
            if (mine == null || updatedAt(row) > updatedAt(mine)) result[key] = row
        }
        return result.values.toList()
    }
}
