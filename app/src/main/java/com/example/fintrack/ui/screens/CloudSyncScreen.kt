package com.example.fintrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.sp
import com.example.fintrack.ui.theme.Emerald600
import com.example.fintrack.ui.theme.Indigo600
import com.example.fintrack.ui.viewmodel.KhataViewModel

@Composable
fun CloudSyncScreen(
    viewModel: KhataViewModel,
    onBack: (() -> Unit)? = null
) {
    val userSession by viewModel.userSession.collectAsState()
    val isBusy by viewModel.isCloudBusy.collectAsState()

    var authTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf(userSession.email ?: "") }
    var password by remember { mutableStateOf("") }
    var supabaseUrl by remember { mutableStateOf(userSession.supabaseUrl) }
    var supabaseAnonKey by remember { mutableStateOf("") }
    var isConfigOpen by remember { mutableStateOf(false) }
    val showConfig = isConfigOpen || !userSession.isAuthenticated

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("cloud_sync_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Screen Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud Database Sync (Supabase)",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Connect Android App & Web Dashboard with PostgreSQL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Connection Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (userSession.isAuthenticated) Emerald600.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (userSession.isAuthenticated) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (userSession.isAuthenticated) Emerald600 else Indigo600,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (userSession.isAuthenticated) "Connected to Supabase" else "Local Mode (Offline-First)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (userSession.isAuthenticated) Emerald600 else Indigo600
                        ) {
                            Text(
                                text = if (userSession.isAuthenticated) "ACTIVE" else "READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (userSession.isAuthenticated)
                            "Signed in as ${userSession.email}. Tap \"Sync All Data Now\" to upload your ledger and download records from other devices." +
                                (userSession.lastSyncTime?.let { "\nLast sync: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "")
                        else
                            "Your records are stored on this device. Sign in with your own Supabase project to back them up and use them on the web dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = userSession.isAuthenticated && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBusy) "Working…" else "Sync All Data Now")
                    }
                }
            }
        }

        // Web Dashboard Pairing Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Indigo600)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Web Dashboard Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Open the Web Dashboard in a browser and sign in with the same Supabase project. Changes made on either side appear on the other after you sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Auth Tabs: Sign In / Sign Up
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TabRow(selectedTabIndex = authTab) {
                        Tab(
                            selected = authTab == 0,
                            onClick = { authTab = 0 },
                            text = { Text("Sign In") }
                        )
                        Tab(
                            selected = authTab == 1,
                            onClick = { authTab = 1 },
                            text = { Text("Create Account") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input"),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.authenticate(
                                register = authTab == 1,
                                supabaseUrl = supabaseUrl,
                                anonKey = supabaseAnonKey,
                                email = email,
                                password = password
                            )
                            password = ""
                        },
                        enabled = !isBusy && email.isNotBlank() && password.isNotEmpty() &&
                            supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_auth_submit"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBusy) "Please wait…" else if (authTab == 0) "Sign In to Cloud" else "Register & Connect")
                    }

                    if (userSession.isAuthenticated) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }

        // Supabase Settings Toggle
        item {
            OutlinedButton(
                onClick = { isConfigOpen = !isConfigOpen },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showConfig) "Hide Supabase Configuration" else "Configure Supabase URL & Key")
            }
        }

        if (showConfig) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Supabase API Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = supabaseUrl,
                            onValueChange = { supabaseUrl = it },
                            label = { Text("Supabase URL") },
                            placeholder = { Text("https://your-project.supabase.co") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = supabaseAnonKey,
                            onValueChange = { supabaseAnonKey = it },
                            label = { Text("Supabase Anon (public) Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}
