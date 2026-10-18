package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.AboutScreenKey
import com.example.rnd_transit_mtl.CurrentTripScreenKey
import com.example.rnd_transit_mtl.HistoryScreenKey
import com.example.rnd_transit_mtl.LocalNavigator
import com.example.rnd_transit_mtl.LocalTripNavigation
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.ProfileScreenKey
import com.example.rnd_transit_mtl.ScreenKey
import com.example.rnd_transit_mtl.SettingsScreenKey
import com.example.rnd_transit_mtl.TripDetailsScreenKey
import com.example.rnd_transit_mtl.TripReviewScreenKey
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_account_circle
import rnd_transit_mtl.shared.generated.resources.ic_home
import rnd_transit_mtl.shared.generated.resources.ic_receipt_long
import rnd_transit_mtl.shared.generated.resources.ic_settings

/**
 * Shared navigation remains above all destination transition animations.
 */
@Composable
fun SharedTopBar() {
    val navigator = LocalNavigator.current
    val navigation = LocalTripNavigation.current
    val currentKey = navigator.current as? ScreenKey

    SharedTopBarContent(
        currentKey = currentKey,
        hasPrevious = navigator.hasPrevious(),
        onBack = {
            if (navigator.current == currentKey) {
                navigation.back()
            }
        },
        onOpenSection = { destination ->
            if (navigator.current == currentKey) {
                navigation.openSection(destination)
            }
        }
    )
}

/**
 * Stateless header presentation.
 */
@Composable
internal fun SharedTopBarContent(
    currentKey: ScreenKey?,
    hasPrevious: Boolean,
    onBack: () -> Unit,
    onOpenSection: (ScreenKey) -> Unit
) {
    val historySelected =
        currentKey == HistoryScreenKey ||
                currentKey is TripDetailsScreenKey ||
                (
                    currentKey is TripReviewScreenKey &&
                            currentKey.mode == TripReviewMode.EDIT
                    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TransitMain)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings stays at the right edge; only the left group scrolls
            // when a window is too narrow for all three destinations.
            Row(
                modifier = Modifier.weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconItem(
                    label = "Home",
                    painter = painterResource(Res.drawable.ic_home),
                    isSelected = currentKey == MainScreenKey,
                    showGo = currentKey != MainScreenKey,
                    onClick = { onOpenSection(MainScreenKey) }
                )
                HeaderIconItem(
                    label = "Profile",
                    painter = painterResource(Res.drawable.ic_account_circle),
                    isSelected = currentKey == ProfileScreenKey || currentKey == AboutScreenKey,
                    onClick = { onOpenSection(ProfileScreenKey) }
                )
                HeaderIconItem(
                    label = "History",
                    painter = painterResource(Res.drawable.ic_receipt_long),
                    isSelected = historySelected,
                    onClick = { onOpenSection(HistoryScreenKey) }
                )
            }
            HeaderIconItem(
                label = "Settings",
                painter = painterResource(Res.drawable.ic_settings),
                isSelected = currentKey == SettingsScreenKey,
                onClick = { onOpenSection(SettingsScreenKey) }
            )
        }

        // The current-trip title overlays the map so GO and trip viewports match.
        if (currentKey !is CurrentTripScreenKey &&
            (currentKey != MainScreenKey || hasPrevious)
        ) {
            PageTitle(
                title = currentKey?.screenTitle ?: "RND Transit",
                layoutScale = 1f,
                highlighted =
                    currentKey == ProfileScreenKey ||
                            currentKey == AboutScreenKey,
                showBack = hasPrevious,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun HeaderIconItem(
    label: String,
    painter: Painter,
    isSelected: Boolean,
    onClick: () -> Unit,
    showGo: Boolean = false
) {
    val foreground = if (isSelected) TransitMain else TransitWhite

    Column(
        modifier = Modifier
            .width(64.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) TransitSelected else TransitMain
            )
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                selected = isSelected
                contentDescription = label
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showGo) {
            Text(
                text = "GO",
                style = MaterialTheme.typography.titleLarge.copy(
                    brush = Brush.horizontalGradient(
                        listOf(TransitWhite, TransitSelected)
                    ),
                    fontSize = 30.sp
                )
            )
        } else {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(34.dp)
            )
        }

        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
