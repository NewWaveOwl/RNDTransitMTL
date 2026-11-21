package com.example.rnd_transit_mtl.launcher

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import com.example.rnd_transit_mtl.navigation.CompassScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey

internal class RouterDeepLinkResolver {
    private val compassMatcher = UriDeepLinkMatcher(
        DeepLinkUri("rndtransit://launch/compass"),
        CompassScreenKey.serializer(),
    )

    fun resolve(uriString: String): List<NavKey>? {
        val request = DeepLinkRequest(uri = uriString)
        val targetKey = compassMatcher.match(request)?.key ?: return null
        return listOf(MainScreenKey, targetKey)
    }
}
