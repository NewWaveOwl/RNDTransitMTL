package com.example.rnd_transit_mtl


object ExternalUriHandler {
    // Holds a URI that arrives before a listener exists
    private var cached: String? = null
    var listener: ((uri: String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                cached?.let { value.invoke(it) }
                cached = null
            }
        }

    fun onNewUri(uri: String) {
        cached = uri
        listener?.let {
            it.invoke(uri)
            cached = null
        }
    }
}