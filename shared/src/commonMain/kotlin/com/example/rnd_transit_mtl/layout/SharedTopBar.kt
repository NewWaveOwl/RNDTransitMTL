package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.AboutScreenKey
import com.example.rnd_transit_mtl.HistoryScreenKey
import com.example.rnd_transit_mtl.LocalNavigator
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.ProfileScreenKey
import com.example.rnd_transit_mtl.ScreenKey
import com.example.rnd_transit_mtl.SettingsScreenKey
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_account_circle
import rnd_transit_mtl.shared.generated.resources.ic_receipt_long
import rnd_transit_mtl.shared.generated.resources.ic_settings

/**
 * Displays Figma navigation icons and shows gradient GO only away from Main.
 */
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
                if (currentKey != MainScreenKey) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.width(88.dp * layoutScale).height(60.dp * layoutScale)
                            .clip(RoundedCornerShape(8.dp * layoutScale))
                            .clickable(role = Role.Button) { navigator.popUntil(MainScreenKey) }
                            .semantics { contentDescription = "Go to trip planner" }
                    ) {
                        Text(
                            text = "GO",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                brush = Brush.horizontalGradient(listOf(TransitWhite, TransitSelected)),
                                fontSize = 50.sp * layoutScale,
                                lineHeight = 58.sp * layoutScale,
                                letterSpacing = 0.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
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
