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
import com.example.rnd_transit_mtl.state.LocalNicknameSession

/**
 * Displays the memory-only nickname, account placeholders, and About action.
 *
 * Uses the shared navigator supplied by App. Email and password labels are static placeholders.
 */
@Composable
fun ProfileScreen() {
    /** Obtain the navigator shared by the application instead of creating a separate back stack. */
    val navigator = LocalNavigator.current
    val nickname = LocalNicknameSession.current.nickname.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        /** Scale the reference layout to the available width, bounded between 0.7 and 1.4. */
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)

        Column(Modifier.fillMaxSize().background(TransitComplementary)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp * layoutScale, vertical = 24.dp * layoutScale),
                verticalArrangement = Arrangement.spacedBy(16.dp * layoutScale)
            ) {
                Text("Nickname", color = TransitWhite, fontSize = 18.sp * layoutScale)
                Text(nickname, color = TransitWhite, fontSize = 28.sp * layoutScale)
                Text("Email", color = TransitWhite, fontSize = 18.sp * layoutScale)
                Text("Change password", color = TransitWhite, fontSize = 18.sp * layoutScale)
            }

            /** Keep the About action below the scrollable account content and push its destination on tap. */
            TextButton(
                onClick = { navigator.navigate(AboutScreenKey) },
                modifier = Modifier.fillMaxWidth().height(64.dp * layoutScale).background(TransitMain)
            ) {
                Text("about app", color = TransitWhite, fontSize = 26.sp * layoutScale)
            }
        }
    }
}
