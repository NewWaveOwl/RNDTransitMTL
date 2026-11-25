package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitOrange
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_warning

// Layout settings — dp dimensions for the warning banner and icon.
private val TripWarningIconSize = 26.dp
private val TripWarningMinimumHeight = 88.dp
private val TripWarningHorizontalSpacing = 12.dp
private val TripWarningTextSpacing = 4.dp

/**
 * Announces a warning while retaining the surrounding trip content.
 *
 * @param title Warning heading.
 * @param detail Explanation of the condition.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripWarning(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TripWarningMinimumHeight)
            .background(TransitOrange)
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(TripWarningHorizontalSpacing),
    ) {
        Image(
            painterResource(Res.drawable.rnd_warning),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(TripWarningIconSize),
        )

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TripWarningTextSpacing),
        ) {
            Text(
                title,
                color = TransitComplementary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                detail,
                color = TransitComplementary,
                fontSize = 14.sp,
            )
        }
    }
}
