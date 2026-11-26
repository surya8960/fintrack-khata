package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AccountType(val displayName: String) {
    BANK("Bank Account"),
    CASH("Cash in Hand"),
    WALLET("Digital Wallet"),
    CREDIT_CARD("Credit Card")
}

@Serializable
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AccountType = AccountType.BANK,
    val balance: Double = 0.0,
    val accountNumber: String = "",
    val colorHex: String = "#047857",
    val updatedAt: Long = System.currentTimeMillis()
)
