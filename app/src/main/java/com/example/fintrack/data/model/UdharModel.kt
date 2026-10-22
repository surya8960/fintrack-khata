package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class UdharType(val title: String, val hinglishTitle: String) {
    GAVE("You Gave", "Udhar Diya"),      // Shopkeeper gave credit / goods on credit (Red)
    GOT("You Got", "Payment Mila")       // Customer made payment / deposited money (Green)
}

@Serializable
data class UdharEntry(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val amount: Double,
    val type: UdharType,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val billNumber: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class UdharCustomer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun calculateBalance(entries: List<UdharEntry>): Double {
        val totalGave = entries.filter { it.customerId == id && it.type == UdharType.GAVE }.sumOf { it.amount }
        val totalGot = entries.filter { it.customerId == id && it.type == UdharType.GOT }.sumOf { it.amount }
        return totalGave - totalGot
    }

    fun calculateTotalGave(entries: List<UdharEntry>): Double {
        return entries.filter { it.customerId == id && it.type == UdharType.GAVE }.sumOf { it.amount }
    }

    fun calculateTotalGot(entries: List<UdharEntry>): Double {
        return entries.filter { it.customerId == id && it.type == UdharType.GOT }.sumOf { it.amount }
    }
}
