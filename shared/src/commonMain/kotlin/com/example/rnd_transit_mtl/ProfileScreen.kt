package com.example.rnd_transit_mtl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/** Profile shell: account details are placeholders; About opens the team page. */
@Composable
fun ProfileScreen() {
    val navigator = LocalNavigator.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)
        Column(Modifier.fillMaxSize().background(TransitComplementary)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp * layoutScale, vertical = 24.dp * layoutScale),
                verticalArrangement = Arrangement.spacedBy(16.dp * layoutScale)
            ) {
                Text("Email", color = TransitWhite, fontSize = 18.sp * layoutScale)
                Text("Change password", color = TransitWhite, fontSize = 18.sp * layoutScale)
            }
            TextButton(
                onClick = { navigator.navigate(AboutScreenKey) },
                modifier = Modifier.fillMaxWidth().height(64.dp * layoutScale).background(TransitMain)
            ) {
                Text("about app", color = TransitWhite, fontSize = 26.sp * layoutScale)
            }
        }
    }
}
