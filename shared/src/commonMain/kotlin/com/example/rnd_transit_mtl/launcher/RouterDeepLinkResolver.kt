package com.example.rnd_transit_mtl.launcher

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import com.example.rnd_transit_mtl.navigation.CompassConfirmationScreenKey
import com.example.rnd_transit_mtl.navigation.CompassScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey

internal class RouterDeepLinkResolver {
    private val compassMatcher = UriDeepLinkMatcher(
        DeepLinkUri("rndtransit://launch/compass"),
        CompassScreenKey.serializer(),
    )
    private val compassConfirmationMatcher = UriDeepLinkMatcher(
        DeepLinkUri("rndtransit://launch/compass/confirm"),
        CompassConfirmationScreenKey.serializer(),
    )
    private val webCompassConfirmationMatcher = UriDeepLinkMatcher(
        DeepLinkUri("http://localhost:8080/compass/confirm"),
        CompassConfirmationScreenKey.serializer(),
    )

    fun resolve(uriString: String): List<NavKey>? {
        val normalizedUri = uriString.replace("/#/", "/")
        val request = DeepLinkRequest(uri = normalizedUri)
        val targetKey = compassConfirmationMatcher.match(request)?.key
            ?: webCompassConfirmationMatcher.match(request)?.key
            ?: compassMatcher.match(request)?.key
            ?: return null
        return listOf(MainScreenKey, targetKey)
    }
}
