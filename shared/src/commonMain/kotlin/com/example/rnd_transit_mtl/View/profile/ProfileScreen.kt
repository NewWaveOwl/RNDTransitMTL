package com.example.rnd_transit_mtl.View.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rnd_transit_mtl.ViewModels.Accounts.ProfileViewModel
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.layout.PageTitle
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_account_circle
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitFilledButton
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton

/** @param viewModel null for guests; otherwise the signed-in user's profile. */
@Composable
internal fun ProfileScreen(viewModel: ProfileViewModel?) {
    val navigator = LocalNavigator.current
    val state = viewModel?.state?.collectAsStateWithLifecycle()?.value



    Column(Modifier.fillMaxSize()) {
        PageTitle(title = ProfileScreenKey.screenTitle, layoutScale = 1f, highlighted = false)
        Column(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF8F8F8))
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Your profile", color = TransitComplementary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            Icon(painterResource(Res.drawable.ic_account_circle), contentDescription = null,
                tint = TransitMain, modifier = Modifier.size(56.dp))

            Text(if (viewModel == null) "Guest" else when {
                    state?.account != null -> state.account.username
                    state?.accountLoadFailed == true -> "Couldn’t load profile"
                    else -> "Loading…"
                },
                color = TransitComplementary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(2.dp))

            if (viewModel != null) {

                TransitOutlineButton(
                    "Edit profile",
                    onClick = {
                        navigator.navigate(SettingsScreenKey)
                    },
                    enabled = viewModel != null

                )

            }



            if (viewModel == null) {
                TransitOutlineButton("Sign in", onClick = { navigator.navigate(AuthScreenKey()) })
                TransitOutlineButton("Create an account", onClick = { navigator.navigate(AuthScreenKey(register = true)) })
            }
            TransitFilledButton("Your trip history", onClick = { navigator.navigate(HistoryScreenKey) })
            TransitOutlineButton("About RND Transit", onClick = { navigator.navigate(AboutScreenKey) })
            if (viewModel != null && state != null) {
                if (state.signOutError) Text("Couldn’t sign out. Check your connection and try again.",
                    color = MaterialTheme.colorScheme.error)
                TransitOutlineButton(if (state.signingOut) "Signing out…" else "Sign out",
                    onClick = viewModel::signOut)
            }
        }
    }
}

