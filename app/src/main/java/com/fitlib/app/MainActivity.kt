package com.fitlib.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fitlib.app.ui.FitLibApp
import com.fitlib.app.ui.FitLibViewModel
import com.fitlib.app.ui.FitLibViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: FitLibViewModel by viewModels {
        FitLibViewModelFactory((application as FitLibApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FitLibApp(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened()
    }
}
