package com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Full-width pill button with a [TransitMain] outline and label.
 *
 * @param height Button height; defaults to the compact Profile size.
 * @param fontSize Label size.
 */
@Composable
fun TransitOutlineButton(label: String, onClick: () -> Unit, height: Dp = 38.dp,
    fontSize: TextUnit = 12.sp) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(height / 2), border = BorderStroke(1.dp, TransitMain)) {
        Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = TransitMain)
    }
}
