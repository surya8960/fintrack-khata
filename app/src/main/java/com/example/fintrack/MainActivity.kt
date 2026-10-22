package com.example.fintrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.fintrack.ui.MainScreen
import com.example.fintrack.ui.theme.FinTrackTheme
import com.example.fintrack.ui.viewmodel.KhataViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: KhataViewModel by viewModels { KhataViewModel.factory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinTrackTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
