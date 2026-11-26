package com.example.fintrack.data.model

import kotlinx.serialization.Serializable

/**
 * Authenticated Supabase session. Kept in memory only (never written to the ledger file),
 * so tokens do not end up in app backups.
 */
@Serializable
data class UserSession(
    val email: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val isAuthenticated: Boolean = false,
    val lastSyncTime: Long? = null,
    val supabaseUrl: String = "",
    val supabaseKey: String = ""
)
