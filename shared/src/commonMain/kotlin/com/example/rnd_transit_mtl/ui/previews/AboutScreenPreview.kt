package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.AboutScreen
import com.example.rnd_transit_mtl.AboutScreenKey
import com.example.rnd_transit_mtl.MainScreenKey

@Preview(showBackground = true, widthDp = 402, heightDp = 716)
@Composable
fun AboutScreenPreview() {
    NavigationPreviewHost(
        MainScreenKey,
        AboutScreenKey
    ) {
        AboutScreen()
    }
}
