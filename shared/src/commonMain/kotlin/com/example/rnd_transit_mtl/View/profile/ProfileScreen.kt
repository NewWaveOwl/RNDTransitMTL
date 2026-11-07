package com.example.rnd_transit_mtl.View.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.LocalAccountSession
import com.example.rnd_transit_mtl.data.authentication.LocalAuthRepository
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.state.user.LocalNicknameSession
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_account_circle

@Composable
fun ProfileScreen() {
    val navigator = LocalNavigator.current
    val nickname = LocalNicknameSession.current.nickname.orEmpty()
    val session = LocalAccountSession.current
    val repository = LocalAuthRepository.current
    val scope = rememberCoroutineScope()
    val signedIn = session as? AccountSession.SignedIn

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8F8F8))
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Your profile", color = TransitComplementary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Icon(painterResource(Res.drawable.ic_account_circle), contentDescription = null,
            tint = TransitMain, modifier = Modifier.size(56.dp))
        Text(nickname.ifBlank { signedIn?.email?.substringBefore("@") ?: "Guest" },
            color = TransitComplementary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(signedIn?.email ?: "Guest", color = TransitComplementary, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        if (signedIn == null) {
            ProfileOutlineButton("Sign in", onClick = { navigator.navigate(AuthScreenKey) })
            ProfileOutlineButton("Create an account", onClick = { navigator.navigate(AuthScreenKey) })
        }
        ProfileFilledButton("Your trip history", onClick = { navigator.navigate(HistoryScreenKey) })
        ProfileOutlineButton("About RND Transit", onClick = { navigator.navigate(AboutScreenKey) })
        if (signedIn != null) {
            ProfileOutlineButton("Sign out", onClick = { scope.launch { repository.signOut() } })
        }
    }
}

@Composable
private fun ProfileFilledButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(38.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TransitMain)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileOutlineButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(38.dp),
        shape = RoundedCornerShape(18.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TransitMain)
    }
}
