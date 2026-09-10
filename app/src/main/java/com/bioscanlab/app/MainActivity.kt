package com.bioscanlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bioscanlab.app.nav.AppNavigation
import com.bioscanlab.app.ui.theme.BioScanLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BioScanLabTheme {
                AppNavigation()
            }
        }
    }
}
