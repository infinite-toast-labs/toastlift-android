package dev.toastlabs.toastlift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.toastlabs.toastlift.ui.ToastLiftApp
import dev.toastlabs.toastlift.ui.ToastLiftViewModel
import dev.toastlabs.toastlift.ui.ToastLiftViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: ToastLiftViewModel by viewModels {
        ToastLiftViewModelFactory((application as ToastLiftApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ToastLiftApp(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened()
    }
}
