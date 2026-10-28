package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.navigation.AboutScreenKey
import com.example.rnd_transit_mtl.navigation.CurrentTripScreenKey
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.ProfileScreenKey
import com.example.rnd_transit_mtl.navigation.ScreenKey
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey
import com.example.rnd_transit_mtl.navigation.TripDetailsScreenKey
import com.example.rnd_transit_mtl.navigation.TripReviewScreenKey
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_account_circle
import rnd_transit_mtl.shared.generated.resources.ic_receipt_long
import rnd_transit_mtl.shared.generated.resources.ic_settings

// Layout settings — button widths and spacing also control how much of the left group scrolls.
private val HeaderButtonWidth = 64.dp
private val HeaderButtonHeight = 68.dp
private val HeaderIconSize = 34.dp
private val HeaderHorizontalPadding = 8.dp
private val HeaderVerticalPadding = 6.dp
private val HeaderButtonSpacing = 6.dp
private val HeaderButtonContentPadding = 4.dp
private val HeaderButtonCornerRadius = 14.dp
private val HeaderReturnToMainButtonWidth = 80.dp
private val HeaderReturnToMainControlWidth = 72.dp
private val HeaderReturnToMainControlHeight = 44.dp
private val HeaderReturnToMainControlCornerRadius = 18.dp

// Appearance settings — Figma Return to Main_v3_updated: blue until 16.8%, then a gentle blend to green.
private val HeaderReturnToMainGradient = Brush.horizontalGradient(
    0.16826923f to TransitMain, 1f to TransitSelected
)

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
                .padding(horizontal = HeaderHorizontalPadding, vertical = HeaderVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(HeaderButtonSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings stays at the right edge; only the left group scrolls
            // when a window is too narrow for all three destinations.
            Row(
                modifier = Modifier.weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(HeaderButtonSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderReturnToMain(
                    isSelected = currentKey == MainScreenKey,
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

/** Displays a native Compose GO button as the existing return-to-Main navigation action.
 * @param isSelected Whether Main is the currently selected destination.
 * @param onClick Opens Main through the destination's existing navigation guard.
 */
@Composable
internal fun HeaderReturnToMain(isSelected: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(HeaderReturnToMainButtonWidth).height(HeaderButtonHeight)
        .clip(RoundedCornerShape(HeaderButtonCornerRadius))
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { selected = isSelected; contentDescription = "Return to Main" },
        contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(HeaderReturnToMainControlCornerRadius)
        Box(Modifier.width(HeaderReturnToMainControlWidth).height(HeaderReturnToMainControlHeight)
            .clip(shape).background(HeaderReturnToMainGradient),
            contentAlignment = Alignment.Center) {
            Text("GO", color = TransitWhite, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeaderIconItem(
    label: String,
    painter: Painter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val foreground = if (isSelected) TransitMain else TransitWhite

    Column(
        modifier = Modifier
            .width(HeaderButtonWidth)
            .height(HeaderButtonHeight)
            .clip(RoundedCornerShape(HeaderButtonCornerRadius))
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
            .padding(HeaderButtonContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(HeaderIconSize)
        )

        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
