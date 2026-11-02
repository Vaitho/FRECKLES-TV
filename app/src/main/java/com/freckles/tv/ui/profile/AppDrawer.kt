package com.freckles.tv.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freckles.tv.data.prefs.Profile
import com.freckles.tv.data.prefs.UserPrefs
import com.freckles.tv.ui.common.Avatar
import com.freckles.tv.ui.theme.Accents
import com.freckles.tv.ui.theme.DYNAMIC
import com.freckles.tv.ui.theme.dynamicAvailable
import kotlinx.coroutines.launch

/** Menu laterale: profilo, aspetto (modalità e colore) e impostazioni. */
@Composable
fun AppDrawer(profile: Profile, prefs: UserPrefs, onProfile: () -> Unit, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(profile, 64.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(profile.name ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Modifica profilo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))

            SectionLabel("Tema")
            val modes = listOf("system" to "Sistema", "light" to "Chiaro", "dark" to "Scuro")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                modes.forEachIndexed { i, (id, label) ->
                    SegmentedButton(
                        selected = profile.themeMode == id,
                        onClick = { scope.launch { prefs.setThemeMode(id) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }

            SectionLabel("Colore")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Accents.forEach { a ->
                    Swatch(
                        brush = Brush.linearGradient(listOf(a.color, a.color)),
                        selected = profile.accent == a.id,
                        description = a.label,
                    ) { scope.launch { prefs.setAccent(a.id) } }
                }
                if (dynamicAvailable()) {
                    Swatch(
                        brush = Brush.sweepGradient(listOf(Color(0xFFEA4335), Color(0xFFFBBC04), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFEA4335))),
                        selected = profile.accent == DYNAMIC,
                        description = "Dinamico (colori dello sfondo)",
                    ) { scope.launch { prefs.setAccent(DYNAMIC) } }
                }
            }
            if (dynamicAvailable()) {
                Text(
                    "L'ultimo cerchio usa i colori del tuo sfondo (Material You).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("Importa ed esporta") },
                icon = { Icon(Icons.Filled.SwapVert, null) },
                selected = false,
                onClick = onSettings,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(brush)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, description, tint = Color.White)
    }
}

