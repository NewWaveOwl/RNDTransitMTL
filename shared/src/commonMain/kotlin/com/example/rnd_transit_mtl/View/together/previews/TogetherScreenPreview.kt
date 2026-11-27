package com.example.rnd_transit_mtl.View.together.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.View.together.TogetherCallbacks
import com.example.rnd_transit_mtl.View.together.TogetherContent
import com.example.rnd_transit_mtl.ViewModels.together.TogetherTripAction
import com.example.rnd_transit_mtl.ViewModels.together.TogetherUiState
import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.group.GroupMember
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

private val previewHost = UserId("preview-host")
private val previewMember = UserId("preview-member")
private val previewGroup = Group(
    id = GroupId("preview-group"),
    joinCode = GroupJoinCode.parse("K7MQ2X"),
    hostId = previewHost,
    members = listOf(GroupMember(previewHost, "Ana", 1L), GroupMember(previewMember, "Ben", 2L)),
)

@Preview(name = "Together — guest (mockup)", showBackground = true, widthDp = 390, heightDp = 842)
@Composable
fun TogetherGuestPreview() {
    RNDTransitTheme {
        TogetherContent(null, null, TogetherTripAction.None, TogetherCallbacks())
    }
}

@Preview(name = "Together — join by code", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun TogetherJoinPreview() {
    RNDTransitTheme {
        TogetherContent(
            TogetherUiState(loading = false, joinFormOpen = true, codeInput = "K7MQ"),
            previewMember, TogetherTripAction.None, TogetherCallbacks(),
        )
    }
}

@Preview(name = "Together — host with active trip", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun TogetherHostPreview() {
    RNDTransitTheme {
        TogetherContent(
            TogetherUiState(loading = false, group = previewGroup),
            previewHost, TogetherTripAction.ShareActiveTrip, TogetherCallbacks(),
        )
    }
}

@Preview(name = "Together — member, trip shared", showBackground = true, widthDp = 320, heightDp = 900)
@Composable
fun TogetherMemberPreview() {
    RNDTransitTheme {
        TogetherContent(
            TogetherUiState(loading = false, group = previewGroup),
            previewMember, TogetherTripAction.StartGroupTrip, TogetherCallbacks(),
        )
    }
}
