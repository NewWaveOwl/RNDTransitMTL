package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Provides navigation operations for the shared Navigation 3 back stack.
 *
 * Changes affect the same stack displayed by Router. Normal Back navigation
 * preserves the root entry, while replacement can change the current entry.
 *
 * @param backStack The stack owned by App and displayed by Router.
 */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    /**
     * Reads the destination at the top of the shared stack without changing it.
     *
     * @return The current destination, or null when the stack is empty.
     */
    val current: NavKey? get() = backStack.lastOrNull()

    /**
     * Adds a destination to the shared back stack.
     *
     * @param key Destination key to push onto the stack.
     */
    fun navigate(key: NavKey) {
        /** Append the destination so Back can return to the previously current entry. */
        backStack += key
    }

    /**
     * Removes the current destination only when a previous entry exists.
     */
    fun pop() {
        /** Keep the root destination when there is no earlier entry to return to. */
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Checks whether Back can return to another destination.
     *
     * @return True when the stack contains more than its root entry.
     */
    fun hasPrevious(): Boolean = backStack.size > 1

    /**
     * Removes entries above the last matching key; a missing key leaves the stack unchanged.
     *
     * @param key Destination key to retain at the top of the stack.
     */
    fun popUntil(key: NavKey) {
        /** Target the most recent matching destination when the same key appears more than once. */
        val index = backStack.indexOfLast { it == key }

        /** An absent destination must not remove existing stack entries. */
        if (index == -1) return

        /** Remove only entries above the target, leaving it as the current destination. */
        while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Replaces the current destination, or adds the first entry when the stack is empty.
     *
     * @param key Destination key that becomes the current entry.
     */
    fun replace(key: NavKey) {
        /** Remove the current entry if present, then add its replacement even when the stack was empty. */
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
        backStack += key
    }
}