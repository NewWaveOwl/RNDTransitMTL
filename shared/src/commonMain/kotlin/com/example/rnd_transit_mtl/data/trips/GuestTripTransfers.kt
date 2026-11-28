package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.data.authentication.AccountSession
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

    private var journal = storage.read(KEY)?.let { Json.decodeFromString<Journal>(it) }

    fun restoreGuest(store: TripsStore) {
        journal?.let { store.restoreGuestHistory(it.guest) }
    }

    fun saveGuest(history: List<Trip>) {
        val current = journal
        if (history == current?.guest.orEmpty()) return

        val savedById = history.associateBy { it.id }
        val pending = current?.pending.orEmpty().mapNotNull { transfer ->
            if (transfer.owner != null) return@mapNotNull transfer

            val updatedTrips = transfer.trips.mapNotNull { savedById[it.id] }
            if (updatedTrips.isEmpty()) null else transfer.copy(trips = updatedTrips)
        }
        write(Journal(history, pending))
    }

    @OptIn(ExperimentalUuidApi::class)
    fun stage(email: String, guest: TripsStore): String? {
        val history = guest.completedTrips + guest.cancelledTrips
        saveGuest(history)
        val pending = journal?.pending.orEmpty()
        val signupEmail = email.trim().lowercase()
        val existingSignup = pending.firstOrNull { it.owner == null && it.signupEmail == signupEmail }
        val reservedIds = pending.asSequence().flatMap { it.trips.asSequence() }.map { it.id }.toSet()
        val trips = history.filter { it.id !in reservedIds }
        if (trips.isEmpty()) return existingSignup?.token

        val transfer = existingSignup?.copy(trips = existingSignup.trips + trips)
            ?: GuestTripTransfer(Uuid.random().toString(), signupEmail, trips)
        write(Journal(history, pending.filterNot { it == existingSignup } + transfer))
        return transfer.token
    }

    fun forAccount(session: AccountSession.SignedIn, guest: TripsStore): List<GuestTripTransfer> {
        val history = guest.completedTrips + guest.cancelledTrips
        saveGuest(history)
        val current = journal ?: return emptyList()
        val signupEmail = session.email?.trim()?.lowercase()
        val pending = current.pending.map { transfer ->
            if (transfer.owner == null && transfer.token == session.guestTransferToken &&
                transfer.signupEmail == signupEmail
            ) {
                transfer.copy(owner = session.userId)
            } else {
                transfer
            }
        }
        if (pending != current.pending) {
            val claimedIds = pending.filter { it.owner == session.userId }
                .flatMap { it.trips }.map { it.id }.toSet()
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
