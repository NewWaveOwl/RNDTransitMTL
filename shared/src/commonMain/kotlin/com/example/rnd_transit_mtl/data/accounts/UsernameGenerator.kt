package com.example.rnd_transit_mtl.data.accounts
import kotlin.random.Random

class UsernameGenerator {



    public companion object {

        private val adjectives = listOf(
            "swift", "quiet", "brave", "lucky", "sunny",
            "clever", "fuzzy", "bold", "calm", "witty"
        )
        private val nouns = listOf(
            "otter", "falcon", "maple", "comet", "panda",
            "river", "fox", "pixel", "cedar", "raven"
        )

        fun generate(): String {
            val adjective = adjectives.random()
            val noun = nouns.random()
            val number = Random.nextInt(1, 10000)
            return "$adjective$noun$number"
        }

    }

}