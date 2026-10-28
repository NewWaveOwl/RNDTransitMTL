package com.example.rnd_transit_mtl.View.about.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.View.about.AboutScreen
import com.example.rnd_transit_mtl.navigation.AboutScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.layout.previews.NavigationPreviewHost

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
