package com.example.rnd_transit_mtl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.example.rnd_transit_mtl.View.together.TogetherCallbacks
import com.example.rnd_transit_mtl.View.together.TogetherContent
import com.example.rnd_transit_mtl.ViewModels.together.GroupConnection
import com.example.rnd_transit_mtl.ViewModels.together.TogetherTripAction
import com.example.rnd_transit_mtl.ViewModels.together.TogetherUiState
import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.group.GroupMember
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the Together tab states from the mockup and checks their actions. */
@OptIn(ExperimentalComposeUiApi::class)
class TogetherContentTest {
    private val host = UserId("host")
    private val member = UserId("member")
    private val group = Group(
        id = GroupId("group-1"),
        joinCode = GroupJoinCode.parse("K7MQ2X"),
        hostId = host,
        members = listOf(GroupMember(host, "Ana", 1L), GroupMember(member, "Ben", 2L)),
    )

    /** Small harness over ImageComposeScene, as in TripProposalContentTest. */
    private class Screen(content: @androidx.compose.runtime.Composable () -> Unit) : AutoCloseable {
        private val scene = ImageComposeScene(412, 1400) { RNDTransitTheme { content() } }
        private var time = 0L

        private fun nodes(): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        }

        fun render(screenshot: String? = null) {
            repeat(4) { time += 16_000_000; scene.render(time).close() }
            if (screenshot != null) {
                scene.render(time).use { image ->
                    val folder = File("build/together-previews").apply { mkdirs() }
                    image.encodeToData()!!.use { File(folder, screenshot).writeBytes(it.bytes) }
                }
            }
        }

        fun texts(): List<String> =
            nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }

        /** Taps the last node showing [label], so a dialog button wins over the button that opened it. */
        fun click(label: String) {
            val node = nodes().last {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
            }
            val point = node.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
            render()
        }

        override fun close() = scene.close()
    }

    @Test
    fun aGuestSeesTheMockupInvitationAndBothButtonsLeadToSignIn() {
        val events = mutableListOf<String>()
        Screen {
            TogetherContent(null, null, TogetherTripAction.None, TogetherCallbacks(onSignIn = { events += "sign-in" }))
        }.use { screen ->
            screen.render("Together-guest.png")
            val texts = screen.texts()
            listOf("Explore together", "Bring friends along", "Shared choices", "Game & quests preview").forEach {
                assertTrue(it in texts, "Missing \"$it\"")
            }

            screen.click("Sign in to create a group")
            screen.click("Join with a code")
            screen.click("Game & quests preview")

            assertEquals(listOf("sign-in", "sign-in"), events)
        }
    }

    @Test
    fun joinStaysDisabledUntilTheCodeIsComplete() {
        var state by mutableStateOf(TogetherUiState(loading = false, joinFormOpen = true, codeInput = "K7M"))
        val events = mutableListOf<String>()
        Screen {
            TogetherContent(state, member, TogetherTripAction.None, TogetherCallbacks(onJoin = { events += "join" }))
        }.use { screen ->
            screen.render("Together-join-form.png")
            assertTrue("Create a group" in screen.texts())
            screen.click("Join group")
            assertEquals(emptyList(), events)

            state = state.copy(codeInput = "K7MQ2X")
            screen.render()
            screen.click("Join group")
            assertEquals(listOf("join"), events)
        }
    }

    @Test
    fun aMemberSeesTheCodeLiveMembersAndTheSharedTrip() {
        val events = mutableListOf<String>()
        val state = TogetherUiState(loading = false, group = group)
        Screen {
            TogetherContent(state, member, TogetherTripAction.StartGroupTrip, TogetherCallbacks(
                onStartTrip = { events += "start" },
                onLeave = { events += "leave" },
            ))
        }.use { screen ->
            screen.render("Together-member.png")
            val texts = screen.texts()
            listOf("Ana's group", "K7MQ2X", "Party of 2", "Ana", "Ben (you)", "Host").forEach {
                assertTrue(it in texts, "Missing \"$it\" in $texts")
            }
            assertFalse("Bring friends along" in texts)

            screen.click("Start the group trip")
            screen.click("Leave group")
            assertEquals(listOf("start", "leave"), events)
        }
    }

    @Test
    fun theHostMustConfirmBeforeEndingTheGroup() {
        val events = mutableListOf<String>()
        Screen {
            TogetherContent(
                TogetherUiState(loading = false, group = group),
                host,
                TogetherTripAction.None,
                TogetherCallbacks(onLeave = { events += "leave" }),
            )
        }.use { screen ->
            screen.render()
            assertTrue("Plan a trip on the map and start it, then share it here." in screen.texts())

            screen.click("End group")
            assertTrue("End the group?" in screen.texts())
            assertEquals(emptyList(), events)

            screen.click("End group")
            assertEquals(listOf("leave"), events)
        }
    }

    @Test
    fun reconnectingAndMessagesAreShown() {
        val events = mutableListOf<String>()
        val state = TogetherUiState(
            loading = false,
            group = group,
            connection = GroupConnection.Reconnecting,
            message = "The host left, so the group has ended. Your trip continues.",
        )
        Screen {
            TogetherContent(state, member, TogetherTripAction.WaitForHost, TogetherCallbacks(onDismissMessage = { events += "ok" }))
        }.use { screen ->
            screen.render("Together-reconnecting.png")
            val texts = screen.texts()
            assertTrue("Reconnecting… the member list may be out of date." in texts)
            assertTrue("Waiting for the host to share a trip." in texts)

            screen.click("OK")
            assertEquals(listOf("ok"), events)
        }
    }
}
