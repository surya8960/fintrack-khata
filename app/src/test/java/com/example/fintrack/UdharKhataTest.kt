package com.example.fintrack

import com.example.fintrack.data.model.UdharCustomer
import com.example.fintrack.data.model.UdharEntry
import com.example.fintrack.data.model.UdharType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdharKhataTest {

    @Test
    fun testUdharCustomer_CreditDebitBalance() {
        val customer = UdharCustomer(
            id = "test-c1",
            name = "Ramesh Kumar",
            phone = "9876543210",
            address = "Main Market",
            notes = "Regular customer"
        )

        // Customer takes udhar ₹5000 (GAVE) and pays back ₹2000 (GOT)
        val entries = listOf(
            UdharEntry(
                id = "e1",
                customerId = customer.id,
                amount = 5000.0,
                type = UdharType.GAVE,
                date = System.currentTimeMillis(),
                note = "Kirana ration items",
                billNumber = "BILL-104"
            ),
            UdharEntry(
                id = "e2",
                customerId = customer.id,
                amount = 2000.0,
                type = UdharType.GOT,
                date = System.currentTimeMillis(),
                note = "Cash partial payment"
            )
        )

        val totalGave = customer.calculateTotalGave(entries)
        val totalGot = customer.calculateTotalGot(entries)
        val balance = customer.calculateBalance(entries)

        assertEquals(5000.0, totalGave, 0.01)
        assertEquals(2000.0, totalGot, 0.01)
        assertEquals(3000.0, balance, 0.01)

        // Adding full settlement payment of ₹3000
        val settledEntries = entries + UdharEntry(
            id = "e3",
            customerId = customer.id,
            amount = 3000.0,
            type = UdharType.GOT,
            date = System.currentTimeMillis(),
            note = "UPI final settlement"
        )
        val finalBalance = customer.calculateBalance(settledEntries)
        assertEquals(0.0, finalBalance, 0.01)
    }

    @Test
    fun testUdharEntry_HinglishTitles() {
        assertEquals("Udhar Diya", UdharType.GAVE.hinglishTitle)
        assertEquals("You Gave", UdharType.GAVE.title)
        assertEquals("Payment Mila", UdharType.GOT.hinglishTitle)
        assertEquals("You Got", UdharType.GOT.title)
    }
}
