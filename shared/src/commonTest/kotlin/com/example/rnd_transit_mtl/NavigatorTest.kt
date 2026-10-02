package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {
    @Test
    fun aboutReturnsToProfileThenHomeAndRootCannotBePopped() {
        val backStack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(backStack)
        navigator.navigate(ProfileScreenKey)
        navigator.navigate(AboutScreenKey)
        assertTrue(navigator.hasPrevious())
        navigator.pop()
        assertEquals(ProfileScreenKey, navigator.current)
        navigator.pop()
        navigator.pop()
        assertEquals(listOf<NavKey>(MainScreenKey), backStack.toList())
        assertFalse(navigator.hasPrevious())
    }

    @Test
    fun tripSettingsReturnsHomeAndRemovesAllIntermediatePages() {
        val backStack = NavBackStack<NavKey>(MainScreenKey, ProfileScreenKey, AboutScreenKey, SettingsScreenKey)
        val navigator = Navigator(backStack)
        navigator.popUntil(MainScreenKey)
        assertEquals(listOf<NavKey>(MainScreenKey), backStack.toList())
        assertFalse(navigator.hasPrevious())
    }

    @Test
    fun popUntilMissingKeyLeavesNavigationUnchanged() {
        val backStack = NavBackStack<NavKey>(MainScreenKey, HistoryScreenKey)
        val navigator = Navigator(backStack)
        navigator.popUntil(AboutScreenKey)
        assertEquals(listOf<NavKey>(MainScreenKey, HistoryScreenKey), backStack.toList())
    }

    @Test
    fun popUntilKeepsTheMostRecentMatchingEntry() {
        val backStack = NavBackStack<NavKey>(MainScreenKey, ProfileScreenKey, AboutScreenKey, ProfileScreenKey, HistoryScreenKey)
        Navigator(backStack).popUntil(ProfileScreenKey)
        assertEquals(listOf<NavKey>(MainScreenKey, ProfileScreenKey, AboutScreenKey, ProfileScreenKey), backStack.toList())
    }

    @Test
    fun replaceKeepsThePreviousPageForBackNavigation() {
        val backStack = NavBackStack<NavKey>(MainScreenKey, SettingsScreenKey)
        val navigator = Navigator(backStack)
        navigator.replace(HistoryScreenKey)
        assertEquals(listOf<NavKey>(MainScreenKey, HistoryScreenKey), backStack.toList())
        navigator.pop()
        assertEquals(MainScreenKey, navigator.current)
    }

    @Test
    fun replaceCanInitializeAnEmptyStack() {
        val backStack = NavBackStack<NavKey>()
        val navigator = Navigator(backStack)
        navigator.replace(MainScreenKey)
        assertEquals(MainScreenKey, navigator.current)
        assertFalse(navigator.hasPrevious())
    }
}
