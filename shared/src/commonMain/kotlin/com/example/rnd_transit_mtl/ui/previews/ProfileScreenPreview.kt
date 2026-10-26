package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.ProfileScreen
import com.example.rnd_transit_mtl.ProfileScreenKey

@Preview(showBackground = true, widthDp = 402, heightDp = 716)
@Composable
fun ProfileScreenPreview() {
    NavigationPreviewHost(
        MainScreenKey,
        ProfileScreenKey
    ) {
        ProfileScreen()
    }
}
