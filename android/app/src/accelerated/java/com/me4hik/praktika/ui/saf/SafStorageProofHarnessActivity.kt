package com.me4hik.praktika.ui.saf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.me4hik.praktika.ui.theme.PraktikaTheme

class SafStorageProofHarnessActivity : ComponentActivity() {

    private val viewModel: SafStorageProofViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PraktikaTheme {
                SafStorageProofHarness(viewModel = viewModel)
            }
        }
    }
}
