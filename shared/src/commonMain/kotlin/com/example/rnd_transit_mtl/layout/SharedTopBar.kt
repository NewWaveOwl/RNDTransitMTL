package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.*

// Layout settings — dimensions are in dp.
private val HeaderHeight = 64.dp
private val HeaderHorizontalPadding = 12.dp
private val HeaderActionSize = 48.dp
private val HeaderIconSize = 32.dp
private val HeaderLogoSize = 64.dp
private val HeaderActionSpacing = 0.dp
private val HeaderLeftPadding = 8.dp
private val HeaderLeftGroupVerticalOffset = 1.dp
private val HeaderGoWidth = 72.dp
private val HeaderGoHeight = 44.dp
private val HeaderGoCornerRadius = 18.dp

// Appearance settings — matches the updated GO instance in Figma header 103:191.
private val HeaderGoGradient = Brush.horizontalGradient(0.264423f to TransitMain, 1f to TransitSelected)
private val HeaderBottomCornerRadius = 16.dp

/** Connects the shared header to the application's navigation owner. */
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
 * Displays the blue header and the existing contextual page title.
 *
 * @param currentKey Destination currently displayed.
 * @param hasPrevious Whether Back has an earlier destination.
 * @param onBack Requests the existing guarded Back operation.
 * @param onOpenSection Opens a shared section without creating a trip.
 */
@Composable
internal fun SharedTopBarContent(
    currentKey: ScreenKey?,
    hasPrevious: Boolean,
    onBack: () -> Unit,
    onOpenSection: (ScreenKey) -> Unit
) {
    val profileSelected =
        currentKey == ProfileScreenKey ||
            currentKey == AboutScreenKey

    val historySelected =
        currentKey == HistoryScreenKey ||
            currentKey is TripDetailsScreenKey ||
            (
                currentKey is TripReviewScreenKey &&
                    currentKey.mode == TripReviewMode.EDIT
            )

    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        bottomStart = HeaderBottomCornerRadius,
                        bottomEnd = HeaderBottomCornerRadius
                    )
                )
                .background(TransitMain)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeaderHeight)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(y = HeaderLeftGroupVerticalOffset)
                        .padding(start = HeaderLeftPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(HeaderActionSpacing)
                ) {
                    HeaderGoShortcut(onClick = { onOpenSection(MainScreenKey) })

                    HeaderAction(
                        painter = painterResource(
                            Res.drawable.rnd_header_history
                        ),
                        label = "History",
                        isSelected = historySelected,
                        onClick = {
                            onOpenSection(HistoryScreenKey)
                        }
                    )
                }

                IconButton(
                    onClick = { onOpenSection(MainScreenKey) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(HeaderLogoSize)
                ) {
                    Image(
                        painter = painterResource(
                            Res.drawable.rnd_logo_on_blue
                        ),
                        contentDescription = "Return to Main",
                        modifier = Modifier.size(HeaderLogoSize)
                    )
                }

                HeaderAction(
                    painter = painterResource(
                        Res.drawable.rnd_header_settings
                    ),
                    label = "Settings",
                    isSelected = currentKey == SettingsScreenKey,
                    onClick = {
                        onOpenSection(SettingsScreenKey)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = HeaderHorizontalPadding)
                )
            }
        }

        // Preserve the existing title and review-aware Back behavior.
        if (
            currentKey !is CurrentTripScreenKey &&
            (currentKey != MainScreenKey || hasPrevious)
        ) {
            PageTitle(
                title = currentKey?.screenTitle ?: "RND Transit",
                layoutScale = 1f,
                highlighted = profileSelected,
                showBack = hasPrevious,
                onBack = onBack
            )
        }
    }
}

/** GO shortcut from the updated Figma header.
 * @param onClick Returns to the existing Main destination without generating a trip.
 */
@Composable
private fun HeaderGoShortcut(onClick: () -> Unit) {
        Box(Modifier.width(HeaderGoWidth).height(HeaderGoHeight)
            .clip(RoundedCornerShape(HeaderGoCornerRadius)).background(HeaderGoGradient)
            .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            Text("GO", color = TransitWhite, fontSize = 20.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold)
        }
}

/**
 * Displays one white header icon with a 48 dp action target.
 *
 * @param painter Exported Figma icon.
 * @param label Accessible action name.
 * @param isSelected Whether its associated section is displayed.
 * @param onClick Requests the section change.
 * @param modifier Placement within the header.
 */
@Composable
private fun HeaderAction(
    painter: Painter,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(HeaderActionSize)
            .semantics { selected = isSelected }
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = TransitWhite,
            modifier = Modifier.size(HeaderIconSize)
        )
    }
}
