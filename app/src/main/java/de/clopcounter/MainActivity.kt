package de.clopcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.clopcounter.ui.ClopNavigation
import de.clopcounter.ui.theme.ClopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClopTheme {
                ClopNavigation()
            }
        }
    }
}
