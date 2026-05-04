package com.gabe.focusblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.gabe.focusblocker.ui.FocusBlockerApp
import com.gabe.focusblocker.ui.FocusBlockerViewModel
import com.gabe.focusblocker.ui.theme.FocusBlockerTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<FocusBlockerViewModel> {
        FocusBlockerViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusBlockerTheme {
                FocusBlockerApp(viewModel = viewModel)
            }
        }
    }
}

