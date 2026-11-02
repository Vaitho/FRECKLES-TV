package com.freckles.tv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.prefs.Profile
import com.freckles.tv.ui.AppRoot
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.onboarding.OnboardingScreen
import com.freckles.tv.ui.theme.FreckleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as FreckleApp
        val prefs = app.prefs

        setContent {
            // null = preferenze ancora in caricamento
            val profile: Profile? by prefs.profile.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (profile?.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // Le icone delle barre di sistema seguono il tema dell'app, non quello di sistema.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            FreckleTheme(dark = dark, accentId = profile?.accent ?: "violet") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val p = profile
                    when {
                        p == null -> Box(Modifier.fillMaxSize())
                        p.name == null -> OnboardingScreen { scope.launch { prefs.setName(it) } }
                        else -> CompositionLocalProvider(LocalRepo provides app.repository) {
                            AppRoot(p, prefs)
                        }
                    }
                }
            }
        }
    }
}
