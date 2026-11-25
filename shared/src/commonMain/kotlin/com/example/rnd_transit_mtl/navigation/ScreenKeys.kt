package com.example.rnd_transit_mtl.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.model.trip.Trip
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Literal sealed-class route hierarchy for Navigation 3 destinations.
 * Preserves serialized `@SerialName` discriminators across package/file splits.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.ScreenKey")
sealed class ScreenKey : NavKey {
    /** User-facing title rendered by the shared page header. */
    abstract val screenTitle: String
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.MainScreenKey")
data object MainScreenKey : ScreenKey() {
    override val screenTitle = "Home"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.CompassScreenKey")
data object CompassScreenKey : ScreenKey() {
    override val screenTitle = "Compass"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.ProfileScreenKey")
data object ProfileScreenKey : ScreenKey() {
    override val screenTitle = "user"
}

/**
 * Authentication route allowing sign-in or registration entry.
 *
 * @property register Whether the form starts in account-registration mode.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.AuthScreenKey")
data class AuthScreenKey(val register: Boolean = false) : ScreenKey() {
    override val screenTitle = "Sign in"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.AboutScreenKey")
data object AboutScreenKey : ScreenKey() {
    override val screenTitle = "about"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.SettingsScreenKey")
data object SettingsScreenKey : ScreenKey() {
    override val screenTitle = "settings"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.HistoryScreenKey")
data object HistoryScreenKey : ScreenKey() {
    override val screenTitle = "history"
}

/**
 * Active or completed trip destination; [trip] supplies the stable ID resolved against [TripsStore].
 *
 * @property trip Snapshot whose ID selects the authoritative runtime trip record.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.CurrentTripScreenKey")
data class CurrentTripScreenKey(
    val trip: Trip,
) : ScreenKey() {
    override val screenTitle = "Current trip"
}

/**
 * Saved trip inspection route.
 *
 * @property tripId Stable identifier of the saved trip to display.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.TripDetailsScreenKey")
data class TripDetailsScreenKey(
    val tripId: TripId,
) : ScreenKey() {
    override val screenTitle = "Trip details"
}

/**
 * Restricted, serializable return destinations for review editing.
 * Saving the origin avoids relying on a transient callback after rotation.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.ReviewOrigin")
sealed class ReviewOrigin {
    /** Returns the [ScreenKey] to navigate back to when editing finishes. */
    abstract fun destination(): ScreenKey

    @Serializable
    @SerialName("com.example.rnd_transit_mtl.ReviewOrigin.History")
    data object History : ReviewOrigin() {
        override fun destination(): ScreenKey = HistoryScreenKey
    }

    /**
     * Returns to a specific saved trip's details screen.
     *
     * @property tripId Stable trip identifier for the target details screen.
     */
    @Serializable
    @SerialName("com.example.rnd_transit_mtl.ReviewOrigin.Details")
    data class Details(
        val tripId: TripId,
    ) : ReviewOrigin() {
        override fun destination(): ScreenKey = TripDetailsScreenKey(tripId)
    }
}

/**
 * Trip review entry/edit destination.
 *
 * @property tripId Stable identifier of the trip being reviewed.
 * @property mode Initial post-trip review versus editing from History or Details.
 * @property origin Return target when [mode] is [TripReviewMode.EDIT].
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.TripReviewScreenKey")
data class TripReviewScreenKey(
    val tripId: TripId,
    val mode: TripReviewMode,
    val origin: ReviewOrigin = ReviewOrigin.History,
) : ScreenKey() {
    override val screenTitle = "Rate your trip"
}

/**
 * Explicit polymorphic serializer configuration for every concrete [ScreenKey] in the back stack.
 */
val backStackConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MainScreenKey::class, MainScreenKey.serializer())
            subclass(CompassScreenKey::class, CompassScreenKey.serializer())
            subclass(ProfileScreenKey::class, ProfileScreenKey.serializer())
            subclass(AuthScreenKey::class, AuthScreenKey.serializer())
            subclass(AboutScreenKey::class, AboutScreenKey.serializer())
            subclass(SettingsScreenKey::class, SettingsScreenKey.serializer())
            subclass(HistoryScreenKey::class, HistoryScreenKey.serializer())
            subclass(CurrentTripScreenKey::class, CurrentTripScreenKey.serializer())
            subclass(TripReviewScreenKey::class, TripReviewScreenKey.serializer())
            subclass(TripDetailsScreenKey::class, TripDetailsScreenKey.serializer())
        }
    }
}
