package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/** Displays the saved URL. Network access happens only in the user's browser. */
@Composable
fun ReviewImageLink(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentColor: Color = TransitMain,
    enabled: Boolean = true
) {
    if (imageUrl.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    var openFailed by remember(imageUrl) { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectionContainer {
            Text(imageUrl, color = contentColor, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(
            onClick = {
                try {
                    uriHandler.openUri(imageUrl)
                    openFailed = false
                } catch (_: Exception) {
                    openFailed = true
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("Open review image") }
        if (openFailed) {
            Text("This link could not be opened. You can copy the URL above.",
                color = contentColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}
