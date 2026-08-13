package com.me4hik.praktika.ui.restore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.me4hik.praktika.ui.theme.PraktikaTheme

class RestoreProofHarnessActivity : ComponentActivity() {

    private val viewModel: RestoreProofViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PraktikaTheme {
                RestoreProofHarness(viewModel = viewModel)
            }
        }
    }
}
