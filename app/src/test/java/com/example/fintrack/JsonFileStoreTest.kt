package com.example.fintrack

import com.example.fintrack.data.local.JsonFileStore
import com.example.fintrack.data.local.KhataSnapshot
import com.example.fintrack.data.local.PendingDelete
import com.example.fintrack.data.model.UdharCustomer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(file: File) = JsonFileStore(file, KhataSnapshot.serializer())

    @Test
    fun missingFileLoadsNull() {
        assertNull(store(File(tmp.root, "a.json")).load())
    }

    @Test
    fun roundTripPreservesData() {
        val file = File(tmp.root, "nested/khata.json")
        val snapshot = KhataSnapshot(
            udharCustomers = listOf(UdharCustomer(id = "u1", name = "Ramesh ₹")),
            pendingDeletes = listOf(PendingDelete("khata_customers", "x"))
        )
        store(file).save(snapshot)
        assertEquals(snapshot, store(file).load())
        assertTrue(File(tmp.root, "nested").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun corruptFileIsKeptAsBackupAndLoadsNull() {
        val file = tmp.newFile("khata.json")
        file.writeText("{ not json")
        assertNull(store(file).load())
        assertTrue(File(tmp.root, "khata.json.corrupt").exists())
    }

    @Test
    fun unknownKeysFromNewerVersionsAreIgnored() {
        val file = tmp.newFile("khata.json")
        file.writeText("""{"futureField": 1, "udharCustomers": []}""")
        assertEquals(KhataSnapshot(), store(file).load())
    }
}
