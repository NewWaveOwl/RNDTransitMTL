package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.SignupRejected
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal const val GUEST_TRANSFER_TOKEN = "guest_transfer_token"

@Serializable
internal data class GuestTripTransfer(
    val token: String,
    val signupEmail: String,
    val trips: List<Trip>,
    val owner: String? = null,
)

internal class GuestTripTransfers(
    private val storage: ChunkedTripSettings = ChunkedTripSettings(),
) {
    @Serializable
    private data class Journal(
        val guest: List<Trip>,
        val pending: List<GuestTripTransfer> = emptyList(),
    )

    private var journal = storage.read(KEY)?.let {
        runCatching { Json.decodeFromString<Journal>(it) }.getOrNull()
    }

    fun restoreGuest(store: TripsStore) {
        journal?.let { store.restoreGuestHistory(it.guest) }
    }

    fun saveGuest(history: List<Trip>) {
        val current = journal
        if (history == current?.guest.orEmpty()) return

        write(Journal(history, current?.pending.orEmpty()))
    }

    suspend fun signUp(email: String, guest: TripsStore, authenticate: suspend (String) -> Boolean): Boolean {
        val existingSignup = pendingSignup(email)
        val token = stage(email, guest)
        try {
            return authenticate(token)
        } catch (rejected: SignupRejected) {
            // A previous attempt may have reached the server.
            if (existingSignup == null) {
                val current = checkNotNull(journal)
                val pending = current.pending.filterNot { it.token == token && it.owner == null }
                write(current.copy(pending = pending))
            }
            throw rejected
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun stage(email: String, guest: TripsStore): String {
        val history = guest.completedTrips + guest.cancelledTrips
        saveGuest(history)
        pendingSignup(email)?.let { return it.token }
        val pending = journal?.pending.orEmpty().filter { it.owner != null }
        val transfer = GuestTripTransfer(Uuid.random().toString(), email.trim().lowercase(), emptyList())
        write(Journal(history, pending + transfer))
        return transfer.token
    }

    private fun pendingSignup(email: String): GuestTripTransfer? {
        val signupEmail = email.trim().lowercase()
        return journal?.pending?.lastOrNull { it.owner == null }?.takeIf { it.signupEmail == signupEmail }
    }

    private fun reservedTripIds(transfers: List<GuestTripTransfer>): Set<TripId> =
        transfers.flatMap { it.trips }.map { it.id }.toSet()

    fun forAccount(session: AccountSession.SignedIn, guest: TripsStore): List<GuestTripTransfer> {
        val history = guest.completedTrips + guest.cancelledTrips
        saveGuest(history)
        val current = journal ?: return emptyList()
        val signup = session.email?.let(::pendingSignup)
        var pending = current.pending
        if (signup != null && signup.token == session.guestTransferToken) {
            pending = pending.filter { it.owner != null }
            val reservedIds = reservedTripIds(pending)
            val trips = history.filter { it.id !in reservedIds }
            if (trips.isNotEmpty()) pending += signup.copy(owner = session.userId, trips = trips)

            val claimedIds = trips.map { it.id }.toSet()
            val remaining = history.filter { it.id !in claimedIds }
            write(Journal(remaining, pending))
            guest.restoreGuestHistory(remaining)
        }
        return pending.filter { it.owner == session.userId }
    }

    fun acknowledge(transfer: GuestTripTransfer) {
        val current = checkNotNull(journal)
        write(current.copy(pending = current.pending - transfer))
    }

    private fun write(updated: Journal) {
        storage.write(KEY, Json.encodeToString(updated))
        journal = updated
    }

    private companion object {
        const val KEY = "transit_guest_trip_transfers"
    }
}
