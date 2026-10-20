package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Shared title and optional Back action.
 *
 * Back is supplied by shared navigation and shown only above a previous entry.
 */
@Composable
internal fun PageTitle(
    title: String,
    layoutScale: Float,
    highlighted: Boolean,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onTitleClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp * layoutScale)
            .background(
                if (highlighted) TransitHighlight else TransitMain
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Back",
                    color = if (highlighted) TransitMain else TransitWhite
                )
            }
        }

        Text(
            text = title,
            color = if (highlighted) TransitMain else TransitWhite,
            fontSize = (
                    if (highlighted) 40.sp else 26.sp
                    ) * layoutScale,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .then(
                    if (onTitleClick != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Show trip information",
                            onClick = onTitleClick
                        ).padding(vertical = 8.dp)
                    } else {
                        Modifier
                    }
                )
        )
    }
}
