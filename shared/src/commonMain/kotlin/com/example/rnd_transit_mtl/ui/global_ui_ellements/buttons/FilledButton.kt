package com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 * Full-width pill button filled with [TransitMain].
 *
 * @param height Button height; defaults to the compact Profile size.
 * @param fontSize Label size.
 * @param enabled Whether the button can be pressed.
 */
@Composable
fun TransitFilledButton(label: String, onClick: () -> Unit, height: Dp = 38.dp,
    fontSize: TextUnit = 12.sp, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(height), enabled = enabled,
        shape = RoundedCornerShape(height / 2),
        colors = ButtonDefaults.buttonColors(containerColor = TransitMain)) {
        Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}
