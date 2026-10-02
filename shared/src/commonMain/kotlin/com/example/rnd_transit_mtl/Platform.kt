package com.example.rnd_transit_mtl

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform