package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.AboutScreenKey
import com.example.rnd_transit_mtl.HistoryScreenKey
import com.example.rnd_transit_mtl.LocalNavigator
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.ProfileScreenKey
import com.example.rnd_transit_mtl.ScreenKey
import com.example.rnd_transit_mtl.SettingsScreenKey
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_account_circle
import rnd_transit_mtl.shared.generated.resources.ic_receipt_long
import rnd_transit_mtl.shared.generated.resources.ic_settings

/** The course's SharedTopBar, with the app's existing Figma navigation icons. */
@Composable
fun SharedTopBar() {
    val navigator = LocalNavigator.current
    val currentKey = navigator.current as? ScreenKey
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(TransitMain)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(68.dp * layoutScale)
                    .background(TransitMain, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .padding(horizontal = 10.dp * layoutScale)
            ) {
                if (navigator.hasPrevious()) {
                    TextButton(onClick = { navigator.pop() }) { Text("Back", color = TransitWhite) }
                }
                IconButton(
                    onClick = { if (navigator.current != ProfileScreenKey) navigator.navigate(ProfileScreenKey) },
                    modifier = Modifier.size(60.dp * layoutScale)
                ) {
                    Icon(painterResource(Res.drawable.ic_account_circle), "User profile", tint = TransitWhite,
                        modifier = Modifier.size(54.dp * layoutScale))
                }
                IconButton(
                    onClick = { if (navigator.current != HistoryScreenKey) navigator.navigate(HistoryScreenKey) },
                    modifier = Modifier.size(60.dp * layoutScale)
                ) {
                    Icon(painterResource(Res.drawable.ic_receipt_long), "History", tint = TransitWhite,
                        modifier = Modifier.size(50.dp * layoutScale))
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { if (navigator.current != SettingsScreenKey) navigator.navigate(SettingsScreenKey) },
                    modifier = Modifier.size(60.dp * layoutScale)
                ) {
                    Icon(painterResource(Res.drawable.ic_settings), "Settings", tint = TransitWhite,
                        modifier = Modifier.size(54.dp * layoutScale))
                }
            }
            if (currentKey != MainScreenKey) {
                PageTitle(
                    title = currentKey?.screenTitle ?: "RND Transit",
                    layoutScale = layoutScale,
                    highlighted = currentKey == ProfileScreenKey || currentKey == AboutScreenKey
                )
            }
        }
    }
}
