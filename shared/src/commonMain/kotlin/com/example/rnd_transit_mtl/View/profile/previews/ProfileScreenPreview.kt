package com.example.rnd_transit_mtl.View.profile.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.View.profile.ProfileScreen
import com.example.rnd_transit_mtl.navigation.ProfileScreenKey
import com.example.rnd_transit_mtl.layout.previews.NavigationPreviewHost

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
