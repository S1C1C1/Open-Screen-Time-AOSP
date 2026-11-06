package org.openscreentime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.theme.OpenScreenTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenScreenTimeTheme {
                HomeScreen()
            }
        }
    }
}
