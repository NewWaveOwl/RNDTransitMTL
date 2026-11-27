package com.example.rnd_transit_mtl.View.together

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.View.together.components.GroupMembersList
import com.example.rnd_transit_mtl.View.together.components.JoinGroupForm
import com.example.rnd_transit_mtl.ViewModels.together.GroupConnection
import com.example.rnd_transit_mtl.ViewModels.together.TogetherTripAction
import com.example.rnd_transit_mtl.ViewModels.together.TogetherUiState
import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.V3Button
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.V3FeaturePadding
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.V3Heading
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.V3Page
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.V3SectionPadding
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_logo_on_blue
import rnd_transit_mtl.shared.generated.resources.rnd_nav_groups

// Layout settings — dp sizes and spacing from the "Explore together" mockup (docs/mockups/together.png).
private val PanelSpacing = 16.dp
private val PanelLogoSize = 52.dp
private val GroupsIconSize = 36.dp
private val CodeCornerRadius = 16.dp
private val CodeHorizontalPadding = 16.dp
private val MessageCornerRadius = 16.dp
// V3SectionPadding and V3FeaturePadding come from ui/global_ui_ellements/trip/TripV3Shared.kt.

/**
 * User actions on the Together screen.
 *
 * @property onGo Returns to the planner.
 * @property onSignIn Opens sign-in; groups need an account.
 * @property onCreate Creates a group.
 * @property onToggleJoin Shows or hides the join-by-code field.
 * @property onCodeChange Receives the code text after each edit.
 * @property onJoin Joins with the typed code.
 * @property onLeave Leaves the group (ends it for everyone when the host leaves).
 * @property onShareTrip Shares the host's active trip.
 * @property onStartTrip Starts the user's copy of the group trip.
 * @property onOpenActiveTrip Opens the trip the user is already on.
 * @property onDismissMessage Clears the shown message.
 * @property onRetry Looks for the user's group again.
 */
internal data class TogetherCallbacks(
    val onGo: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onToggleJoin: () -> Unit = {},
    val onCodeChange: (String) -> Unit = {},
    val onJoin: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onShareTrip: () -> Unit = {},
    val onStartTrip: () -> Unit = {},
    val onOpenActiveTrip: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Together tab: "Explore together" with a teal panel to create or join a group, which
 * turns into the live group (code, members, trip action, leave) once the user is in one.
 *
 * @param state Screen state, or null for a guest, who must sign in first.
 * @param currentUserId The signed-in user, or null for a guest.
 * @param tripAction The trip action to offer; see togetherTripAction.
 * @param callbacks User actions.
 * @param modifier Configuration applied to the page.
 */
@Composable
internal fun TogetherContent(
    state: TogetherUiState?,
    currentUserId: UserId?,
    tripAction: TogetherTripAction,
    callbacks: TogetherCallbacks,
    modifier: Modifier = Modifier,
) {
    V3Page(modifier) {
        V3Heading(title = "Explore together", subtitle = null, onGo = callbacks.onGo, enabled = true)

        Column(
            Modifier.fillMaxWidth().background(TransitComplementary).padding(V3FeaturePadding),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing),
        ) {
            Image(
                painterResource(Res.drawable.rnd_logo_on_blue),
                contentDescription = null,
                modifier = Modifier.size(PanelLogoSize),
            )
            val group = state?.group
            when {
                state?.loading == true -> CircularProgressIndicator(color = TransitSelected)
                group != null && currentUserId != null ->
                    GroupPanel(group, state, currentUserId, tripAction, callbacks)
                else -> InvitationPanel(state, callbacks)
            }
        }

        state?.message?.let { message ->
            MessageCard(message, onDismiss = callbacks.onDismissMessage)
        }

        Column(
            Modifier.fillMaxWidth().background(TransitSelected).padding(V3FeaturePadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Shared choices",
                color = TransitComplementary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Time, modes and restrictions are agreed before one shared trip is generated.",
                color = TransitComplementary,
                fontSize = 14.sp,
            )
        }

        Column(Modifier.fillMaxWidth().padding(V3FeaturePadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Games are research for #104; the entry point is shown but not active yet.
            V3Button(label = "Game & quests preview", onClick = {}, enabled = false, secondary = true)
            Text("Coming soon", color = TransitComplementary, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Text(
            "A group ends when its host leaves. Everyone keeps their own trip history.",
            color = TransitWhite,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().background(TransitComplementary).padding(V3SectionPadding),
        )
    }
}

/** Before joining: the mockup's "Bring friends along" invitation. */
@Composable
private fun ColumnScope.InvitationPanel(state: TogetherUiState?, callbacks: TogetherCallbacks) {
    val signedIn = state != null
    val busy = state?.busy == true
    Text(
        "Bring friends along",
        color = TransitWhite,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Create or join a shared adventure. Sign in first; everyone agrees to the same trip choices.",
        color = TransitWhite,
        fontSize = 14.sp,
    )
    Image(
        painterResource(Res.drawable.rnd_nav_groups),
        contentDescription = null,
        colorFilter = ColorFilter.tint(TransitSelected),
        modifier = Modifier.size(GroupsIconSize),
    )
    V3Button(
        label = if (signedIn) "Create a group" else "Sign in to create a group",
        onClick = if (signedIn) callbacks.onCreate else callbacks.onSignIn,
        enabled = !busy,
    )
    V3Button(
        label = "Join with a code",
        onClick = if (signedIn) callbacks.onToggleJoin else callbacks.onSignIn,
        enabled = !busy,
        secondary = true,
    )
    if (state?.joinFormOpen == true) {
        JoinGroupForm(
            code = state.codeInput,
            codeValid = state.codeValid,
            busy = busy,
            onCodeChange = callbacks.onCodeChange,
            onJoin = callbacks.onJoin,
        )
    }
}

/** In a group: code to share, live members, the trip action and Leave. */
@Composable
private fun ColumnScope.GroupPanel(
    group: Group,
    state: TogetherUiState,
    currentUserId: UserId,
    tripAction: TogetherTripAction,
    callbacks: TogetherCallbacks,
) {
    val isHost = group.isHost(currentUserId)
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    Text(
        if (isHost) "Your group" else "${group.host?.displayName ?: "The host"}'s group",
        color = TransitWhite,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Join code", color = TransitWhite, fontSize = 14.sp)
        Text(
            group.joinCode.value,
            color = TransitComplementary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier
                .background(TransitSelected, RoundedCornerShape(CodeCornerRadius))
                .padding(horizontal = CodeHorizontalPadding, vertical = 4.dp),
        )
    }
    Text("Share this code with friends so they can join.", color = TransitWhite, fontSize = 14.sp)

    if (state.connection == GroupConnection.Reconnecting) {
        Text(
            "Reconnecting… the member list may be out of date.",
            color = TransitComplementary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(TransitHighlight, RoundedCornerShape(MessageCornerRadius))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    Text(
        "Party of ${group.partySize}",
        color = TransitSelected,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
    GroupMembersList(group.members, group.hostId, currentUserId)

    TripActionArea(tripAction, isHost, state.busy, callbacks)

    V3Button(
        label = if (isHost) "End group" else "Leave group",
        onClick = { if (isHost) confirmLeave = true else callbacks.onLeave() },
        enabled = !state.busy,
        secondary = true,
    )

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("End the group?") },
            text = { Text("Everyone leaves and the code stops working. Trips already started keep going.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; callbacks.onLeave() }) { Text("End group") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Keep group") }
            },
        )
    }
}

@Composable
private fun TripActionArea(
    action: TogetherTripAction,
    isHost: Boolean,
    busy: Boolean,
    callbacks: TogetherCallbacks,
) {
    when (action) {
        TogetherTripAction.ShareActiveTrip ->
            V3Button(label = "Share my current trip", onClick = callbacks.onShareTrip, enabled = !busy)
        TogetherTripAction.StartGroupTrip ->
            V3Button(label = "Start the group trip", onClick = callbacks.onStartTrip, enabled = !busy)
        TogetherTripAction.OnGroupTrip ->
            V3Button(label = "Open the group trip", onClick = callbacks.onOpenActiveTrip, enabled = !busy)
        TogetherTripAction.FinishCurrentTripFirst ->
            PanelHint("The host shared a trip. Finish your current trip to join it.")
        TogetherTripAction.WaitForHost ->
            PanelHint("Waiting for the host to share a trip.")
        TogetherTripAction.None ->
            if (isHost) PanelHint("Plan a trip on the map and start it, then share it here.")
    }
}

@Composable
private fun PanelHint(text: String) {
    Text(text, color = TransitWhite, fontSize = 14.sp)
}

@Composable
private fun MessageCard(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(V3SectionPadding)
            .background(TransitHighlight.copy(alpha = 0.25f), RoundedCornerShape(MessageCornerRadius))
            .padding(start = V3SectionPadding)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = TransitComplementary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("OK", color = TransitComplementary) }
    }
}
