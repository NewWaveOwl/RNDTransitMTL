package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Mutates the application's single Navigation 3 back stack.
 *
 * It contains no Trip records, store operations, or simulation logic.
 * All operations must run on the UI thread.
 */
class Navigator(
    private val backStack: NavBackStack<NavKey>
) {
    val current: NavKey?
        get() = backStack.lastOrNull()

    fun hasPrevious(): Boolean = backStack.size > 1

    /**
     * Repeated taps targeting the current key do not append duplicates.
     */
    fun navigate(key: NavKey) {
        if (current != key) {
            backStack += key
        }
    }

    fun pop() {
        if (hasPrevious()) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * A missing target leaves the stack unchanged.
     */
    fun popUntil(key: NavKey) {
        val index = backStack.indexOfLast { it == key }
        if (index == -1) return

        while (backStack.lastIndex > index) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * Opens one existing instance when available, otherwise pushes it.
     */
    fun open(key: NavKey) {
        if (findLast { it == key } != null) {
            popUntil(key)
        } else {
            navigate(key)
        }
    }

    /**
     * Replaces a non-root destination.
     *
     * An empty stack can be initialized. A one-entry stack keeps its
     * root and pushes a different destination above it.
     */
    fun replace(key: NavKey) {
        if (current == key) return

        when {
            backStack.isEmpty() -> backStack += key
            backStack.size == 1 -> backStack += key
            else -> {
                backStack.removeAt(backStack.lastIndex)
                backStack += key
            }
        }
    }

    fun findLast(
        predicate: (NavKey) -> Boolean
    ): NavKey? = backStack.lastOrNull(predicate)

    /**
     * Removes obsolete non-root entries without exposing the mutable stack.
     */
    fun removeWhere(
        predicate: (NavKey) -> Boolean
    ) {
        for (index in backStack.lastIndex downTo 1) {
            if (predicate(backStack[index])) {
                backStack.removeAt(index)
            }
        }
    }
}
