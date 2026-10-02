package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Navigation 3 helper following Day 14/15, slide 39. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    val current: NavKey? get() = backStack.lastOrNull()

    fun navigate(key: NavKey) {
        backStack += key
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun hasPrevious(): Boolean = backStack.size > 1

    fun popUntil(key: NavKey) {
        val index = backStack.indexOfLast { it == key }
        if (index == -1) return
        while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
        backStack += key
    }
}
