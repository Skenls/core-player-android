package com.coreplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.coreplayer.app.ui.navigation.AppNavigation
import com.coreplayer.app.ui.theme.CorePlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isAmoled by CorePlayerApp.instance.amoledFlow.collectAsState()
            CorePlayerTheme(amoled = isAmoled) {
                AppNavigation()
            }
        }
    }
}
