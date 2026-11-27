package com.example.rnd_transit_mtl.data.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime


/**
 * The app's single Supabase client. Realtime delivers live group membership
 * changes; its socket opens only when a channel is subscribed.
 */
internal object SupabaseBackend {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = "https://vavhdqxzwrymnrzuccax.supabase.co",
            supabaseKey = "sb_publishable_z1_DUb_rMXGHC3E1ptwvIg_SWat717_",
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
