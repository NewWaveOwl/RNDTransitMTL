package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import com.example.rnd_transit_mtl.navigation.HistoryConfirmationScreenKey
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey

internal class RouterDeepLinkResolver {
    private val historyMatcher = UriDeepLinkMatcher(
        DeepLinkUri("rndtransit://launch/history"),
        HistoryScreenKey.serializer(),
    )
    private val historyConfirmationMatcher = UriDeepLinkMatcher(
        DeepLinkUri("rndtransit://launch/history/confirm"),
        HistoryConfirmationScreenKey.serializer(),
    )
    private val webHistoryConfirmationMatcher = UriDeepLinkMatcher(
        DeepLinkUri("http://localhost:8080/history/confirm"),
        HistoryConfirmationScreenKey.serializer(),
    )

    fun resolve(uriString: String): List<NavKey>? {
        val normalizedUri = uriString.replace("/#/", "/")
        val request = DeepLinkRequest(uri = normalizedUri)
        val targetKey = historyConfirmationMatcher.match(request)?.key
            ?: webHistoryConfirmationMatcher.match(request)?.key
            ?: historyMatcher.match(request)?.key
            ?: return null
        return listOf(MainScreenKey, targetKey)
    }
}