package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.ui.LandingContent

/** Coordinates the introductory nickname form; this is not authentication. */
@Composable
fun LandingScreen(
    onLogin: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var nickname by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    LandingContent(
        nickname = nickname,
        showError = showError,
        onNicknameChange = {
            nickname = it
            showError = false
        },
        onContinue = { showError = !onLogin(nickname) },
        modifier = modifier
    )
}
