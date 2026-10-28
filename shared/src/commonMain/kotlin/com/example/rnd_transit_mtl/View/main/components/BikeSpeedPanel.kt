package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import kotlin.math.roundToInt

// Layout settings — horizontal padding controls the responsive slider width.
private val BikePanelHorizontalPadding = 24.dp
private val BikePanelVerticalPadding = 16.dp
private val BikePanelContentSpacing = 8.dp
private val BikeSpeedSliderHeight = 64.dp
private val BikeSliderWithPaceHeight = 99.dp
private val BikePaceBadgeTopOffset = 36.dp
private val BikePaceBadgeHeight = 63.dp
private val BikePaceBadgeWidthFraction = 147f / 332f
private val BikePaceBadgeCornerRadius = 18.dp
private val BikePaceBadgeTextBottomPadding = 8.dp

/** Cycling speed preference using the attraction slider's fill and gesture behavior.
 * @param speedKmh Selected whole-number speed in km/h.
 * @param onSpeedChange Requests a new speed; the ViewModel owns bounds and storage.
 * @param scale Responsive size multiplier relative to the 412 px design.
 * @param enabled Whether the user can change speed.
 */
@Composable
internal fun BikeSpeedPanel(speedKmh: Int, onSpeedChange: (Int) -> Unit, scale: Float, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().background(TransitComplementary)
        .padding(horizontal = BikePanelHorizontalPadding * scale, vertical = BikePanelVerticalPadding * scale),
        verticalArrangement = Arrangement.spacedBy(BikePanelContentSpacing * scale)) {
        Text("Biking speed", color = TransitWhite, fontSize = 20.sp * scale,
            lineHeight = 28.sp * scale, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(BikeSliderWithPaceHeight * scale)) {
            // Draw the badge first: the slider covers its upper portion, as in PROPER_BIKE_BOXSLIDER.
            Box(Modifier.align(Alignment.TopCenter).offset(y = BikePaceBadgeTopOffset * scale)
                .fillMaxWidth(BikePaceBadgeWidthFraction).height(BikePaceBadgeHeight * scale)
                .clip(RoundedCornerShape(BikePaceBadgeCornerRadius * scale))
                .background(Brush.horizontalGradient(listOf(TransitMain, TransitSelected))),
                contentAlignment = Alignment.BottomCenter) {
                val pace = MainPlannerViewModel.bikePaceLabel(speedKmh)
                Text(if (pace == "Fast") "speedy" else pace.lowercase(), color = TransitWhite,
                    fontSize = 20.sp * scale, lineHeight = 28.sp * scale, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = BikePaceBadgeTextBottomPadding * scale))
            }
            IntensitySlider(scale, speedKmh.toFloat(), { if (it.isFinite()) onSpeedChange(it.roundToInt()) },
                modifier = Modifier.fillMaxWidth().height(BikeSpeedSliderHeight * scale),
                valueRange = MainPlannerViewModel.MIN_BIKE_SPEED_KMH.toFloat()..MainPlannerViewModel.MAX_BIKE_SPEED_KMH.toFloat(),
                label = "Biking speed in kilometres per hour", valueText = speedKmh.toString(), enabled = enabled,
                valueFontSizeSp = 26f, roundFilledEnd = true, trackColor = TransitWhite)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Slow · 8 km/h", color = TransitWhite, fontSize = 13.sp * scale)
            Text("Fast · 32 km/h", color = TransitWhite, fontSize = 13.sp * scale)
        }
    }
}
