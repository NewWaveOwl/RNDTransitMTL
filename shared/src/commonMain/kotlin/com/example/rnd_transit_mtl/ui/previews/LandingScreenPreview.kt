package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.ui.LandingContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

@Preview(name = "Nickname landing — phone", widthDp = 350, heightDp = 780)
@Composable
fun LandingPhonePreview() {
    LandingExample()
}

@Preview(name = "Nickname landing — desktop", widthDp = 1000, heightDp = 800)
@Composable
fun LandingDesktopPreview() {
    LandingExample()
}

@Preview(name = "Nickname landing — blank error", widthDp = 350, heightDp = 600)
@Composable
fun LandingErrorPreview() {
    LandingExample(nickname = "", showError = true)
}

@Composable
private fun LandingExample(nickname: String = "Atiom", showError: Boolean = false) {
    RNDTransitTheme {
        LandingContent(
            nickname = nickname,
            showError = showError,
            onNicknameChange = {},
            onContinue = {}
        )
    }
}
