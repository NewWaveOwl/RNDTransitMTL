package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey

internal class RouterDeepLinkResolver {
    private val historyMatcher = UriDeepLinkMatcher(
        DeepLinkUri("rndtransit://launch/history"),
        HistoryScreenKey.serializer(),
    )

    fun resolve(uriString: String): List<NavKey>? {
        val request = DeepLinkRequest(uri = uriString)
        val targetKey = historyMatcher.match(request)?.key ?: return null
        return listOf(MainScreenKey, targetKey)
    }
}