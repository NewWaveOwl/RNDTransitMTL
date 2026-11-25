package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_field_chevron
import rnd_transit_mtl.shared.generated.resources.rnd_field_location

// Layout settings — dp dimensions shared across the proposal and loading screens.
internal val ProposalPanelMaxWidth = 620.dp
private val ProposalFieldMinimumHeight = 68.dp
private val ProposalFieldCornerRadius = 16.dp
private val ProposalFieldStartPadding = 14.dp
private val ProposalFieldEndPadding = 12.dp
private val ProposalFieldVerticalPadding = 7.dp
private val ProposalFieldIconSize = 26.dp
private val ProposalFieldChevronSize = 24.dp
private val ProposalFieldContentSpacing = 12.dp
private val ProposalFieldLabelValueSpacing = 3.dp
private val ProposalButtonMinimumHeight = 52.dp
private val ProposalButtonCornerRadius = 24.dp

/**
 * RND / Field / Label & value.
 * The loading field uses the origin/disclosure variant; the destination uses Figma's text-entry variant.
 *
 * @param label Field caption.
 * @param value Displayed location or destination.
 * @param modifier Parent layout constraints.
 * @param showLocationIcon Whether the Figma origin icon and disclosure chevron are shown.
 */
@Composable
internal fun TripLabelValueField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showLocationIcon: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ProposalFieldCornerRadius),
        color = TransitWhite,
        border = BorderStroke(1.dp, TransitMain),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = ProposalFieldMinimumHeight)
                .padding(
                    start = ProposalFieldStartPadding,
                    end = ProposalFieldEndPadding,
                    top = ProposalFieldVerticalPadding,
                    bottom = ProposalFieldVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProposalFieldContentSpacing),
        ) {
            if (showLocationIcon) {
                Image(
                    painterResource(Res.drawable.rnd_field_location),
                    contentDescription = null,
                    modifier = Modifier.size(ProposalFieldIconSize),
                )
            }

            Column(
                modifier = Modifier.weight(1f).align(Alignment.Top),
                verticalArrangement = Arrangement.spacedBy(ProposalFieldLabelValueSpacing),
            ) {
                Text(
                    text = label,
                    color = TransitComplementary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Text(
                    text = value,
                    color = TransitComplementary,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showLocationIcon) {
                Image(
                    painterResource(Res.drawable.rnd_field_chevron),
                    contentDescription = null,
                    modifier = Modifier.size(ProposalFieldChevronSize),
                )
            }
        }
    }
}

/**
 * Proposal action button with the supplied primary/secondary treatment.
 *
 * @param label Visible action label.
 * @param primary Whether to use the blue primary style.
 * @param onClick Requests the parent-owned action.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun ProposalButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = ProposalButtonMinimumHeight),
        shape = RoundedCornerShape(ProposalButtonCornerRadius),
        border = if (primary) null else BorderStroke(1.dp, TransitMain),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) TransitMain else TransitWhite,
            contentColor = if (primary) TransitWhite else TransitMain,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

/**
 * Sample-map OpenStreetMap attribution pill shown when no live map provider is active.
 *
 * @param modifier Placement within the map.
 */
@Composable
internal fun SampleMapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = "© OpenStreetMap contributors · Sample map",
        color = TransitComplementary,
        fontSize = 10.sp,
        modifier = modifier
            .background(TransitWhite, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
