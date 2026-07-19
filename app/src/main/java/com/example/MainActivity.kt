package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdventurePlayScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AdventureViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: AdventureViewModel = viewModel()
                var activeAdventureId by remember { mutableStateOf<Int?>(null) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (activeAdventureId == null) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onAdventureSelected = { id ->
                                    activeAdventureId = id
                                    viewModel.loadAdventure(id)
                                }
                            )
                        } else {
                            AdventurePlayScreen(
                                viewModel = viewModel,
                                onBack = {
                                    activeAdventureId = null
                                    viewModel.resetActiveAdventure()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
