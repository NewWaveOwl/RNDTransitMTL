package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.*

// Layout settings — dimensions are in dp.
private val BottomBarMinimumHeight = 72.dp
private val BottomTabMinimumHeight = 64.dp
private val BottomTabTopPadding = 4.dp
private val BottomIndicatorWidth = 64.dp
private val BottomIndicatorHeight = 32.dp
private val BottomIndicatorCornerRadius = 16.dp
private val BottomIconSize = 24.dp
private val BottomGroupsIconSize = 26.dp
private val BottomLabelSpacing = 4.dp
private val BottomLabelFontSize = 12.sp
private const val BottomDisabledAlpha = 0.45f

/** Main-view choices represented by the shared bottom navigation. */
enum class MainViewTab {
    Map,
    Compass,
    Together
}

/**
 * Displays the Figma bottom navigation without owning navigation state.
 *
 * @param selectedTab Current main view, or null on another section.
 * @param enabledTabs Views that currently have a working destination.
 * @param onSelect Requests navigation to the chosen view.
 * @param modifier Layout supplied by the scaffold.
 */
@Composable
fun SharedBottomBar(
    selectedTab: MainViewTab?,
    enabledTabs: Set<MainViewTab>,
    onSelect: (MainViewTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TransitWhite)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom +
                        WindowInsetsSides.Horizontal
                )
            )
            .heightIn(min = BottomBarMinimumHeight)
            .selectableGroup(),
        verticalAlignment = Alignment.Top
    ) {
        MainViewTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val enabled = tab in enabledTabs
            val label = tab.name

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BottomTabMinimumHeight)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(tab) }
                    )
                    .semantics {
                        if (!enabled) {
                            stateDescription = "Not available yet"
                        }
                    }
                    .alpha(
                        if (enabled) 1f else BottomDisabledAlpha
                    )
                    .padding(top = BottomTabTopPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = BottomIndicatorWidth,
                            height = BottomIndicatorHeight
                        )
                        .background(
                            color = if (isSelected) {
                                TransitMain
                            } else {
                                TransitWhite
                            },
                            shape = RoundedCornerShape(
                                BottomIndicatorCornerRadius
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (tab) {
                        MainViewTab.Map -> {
                            Icon(
                                painter = painterResource(
                                    Res.drawable.rnd_nav_map
                                ),
                                contentDescription = null,
                                tint = if (isSelected) {
                                    TransitWhite
                                } else {
                                    TransitComplementary
                                },
                                modifier = Modifier.size(
                                    BottomIconSize
                                )
                            )
                        }

                        MainViewTab.Compass -> {
                            // Preserve the teal ring and white active needle.
                            Image(
                                painter = painterResource(
                                    if (isSelected) {
                                        Res.drawable
                                            .rnd_nav_compass_active
                                    } else {
                                        Res.drawable.rnd_nav_compass
                                    }
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(
                                    BottomIconSize
                                )
                            )
                        }

                        MainViewTab.Together -> {
                            Icon(
                                painter = painterResource(
                                    Res.drawable.rnd_nav_groups
                                ),
                                contentDescription = null,
                                tint = if (isSelected) {
                                    TransitWhite
                                } else {
                                    TransitComplementary
                                },
                                modifier = Modifier.size(
                                    BottomGroupsIconSize
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(BottomLabelSpacing))

                Text(
                    text = label,
                    color = if (isSelected) {
                        TransitMain
                    } else {
                        TransitComplementary
                    },
                    fontSize = BottomLabelFontSize,
                    fontWeight = if (isSelected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
                )
            }
        }
    }
}
