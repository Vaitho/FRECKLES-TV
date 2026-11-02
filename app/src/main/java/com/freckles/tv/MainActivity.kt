package com.freckles.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.ui.AppRoot
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.onboarding.OnboardingScreen
import com.freckles.tv.ui.theme.FreckleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = (application as FreckleApp).prefs
        val repository = (application as FreckleApp).repository

        setContent {
            FreckleTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val state by prefs.username.collectAsStateWithLifecycle(initialValue = "0000loading")
                    val name = state
                    if (name == "0000loading") {
                        Box(Modifier.fillMaxSize())
                    } else if (name == null) {
                        OnboardingScreen { scope.launch { prefs.setUsername(it) } }
                    } else {
                        CompositionLocalProvider(LocalRepo provides repository) {
                            AppRoot(name)
                        }
                    }
                }
            }
        }
    }
}
