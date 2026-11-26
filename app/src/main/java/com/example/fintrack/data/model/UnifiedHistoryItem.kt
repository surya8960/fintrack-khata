package com.example.fintrack.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class HistoryDomain(val label: String, val hinglishLabel: String) {
    ALL("All", "Sabhi"),
    KHATA("Udhar Khata", "Udhar Khata"),
    LOANS("Interest Loans", "Byaaj Loan"),
    ACCOUNTS("Accounts", "Khata & Cash")
}

@Serializable
data class UnifiedHistoryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val date: Long,
    val domain: HistoryDomain,
    val isCredit: Boolean, // true -> money coming in (green), false -> money going out (red)
    val badgeText: String,
    val notes: String = ""
)
