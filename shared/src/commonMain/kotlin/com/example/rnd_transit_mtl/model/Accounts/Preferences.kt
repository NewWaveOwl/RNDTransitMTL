package com.example.rnd_transit_mtl.model.Accounts
import androidx.compose.animation.TransformScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.serialization.Serializable


enum class TransportModes{
    WALK, BUS, BIKE
}

@Serializable
data class Preferences(
    val appColor: String?,
    val transportMode: TransportModes,
    val availabeTime: DateTimeUnit.TimeBased
) {


}
