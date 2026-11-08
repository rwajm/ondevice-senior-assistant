package com.k2j3.seniorassistant.device

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.k2j3.seniorassistant.device.ui.navigation.AppNavDisplay
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeniorAssistantTheme {
                AppNavDisplay()
            }
        }
    }
}
