package com.rameshta.magnetrail.core.daily

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

data class DailyIdentity(
    val localDate: LocalDate,
    val generatorVersion: Int,
    val dailyId: String,
    val seed: Long,
)

object DailySeed {
    const val GENERATOR_VERSION = 1
    const val FIXED_PUBLIC_SALT = "magnetrail-daily-public-salt-v1"

    fun identity(
        localDate: LocalDate,
        generatorVersion: Int = GENERATOR_VERSION,
        salt: String = FIXED_PUBLIC_SALT,
    ): DailyIdentity {
        require(generatorVersion > 0) { "generatorVersion must be positive" }
        val dailyId = "$localDate-v$generatorVersion"
        val material = "Magnetrail|$localDate|$generatorVersion|$salt"
        return DailyIdentity(
            localDate = localDate,
            generatorVersion = generatorVersion,
            dailyId = dailyId,
            seed = stableHash64(material),
        )
    }

    /** First signed 64 bits of SHA-256 in network byte order. */
    fun stableHash64(value: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long
    }
}
