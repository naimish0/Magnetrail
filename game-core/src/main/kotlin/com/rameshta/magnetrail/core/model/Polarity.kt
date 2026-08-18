package com.rameshta.magnetrail.core.model

enum class Polarity {
    PULL,
    PUSH,
    ;

    fun flipped(): Polarity = when (this) {
        PULL -> PUSH
        PUSH -> PULL
    }

    companion object {
        fun fromName(value: String): Polarity = entries.firstOrNull { it.name == value.uppercase() }
            ?: throw IllegalArgumentException("Unknown polarity '$value'; expected PULL or PUSH")
    }
}
