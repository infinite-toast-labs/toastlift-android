package dev.toastlabs.toastlift.data

import java.io.File

class DataEnvironment private constructor(
    val databaseFile: File?,
    val debugSessionId: String?,
    val featureFlags: Map<String, Any?>,
    val frozenTimeIso: String?,
    val randomSeed: Long?,
) {
    val isDebugEphemeral: Boolean get() = debugSessionId != null

    companion object {
        fun real(): DataEnvironment = DataEnvironment(
            databaseFile = null,
            debugSessionId = null,
            featureFlags = emptyMap(),
            frozenTimeIso = null,
            randomSeed = null,
        )

        fun debugEphemeral(
            databaseFile: File,
            debugSessionId: String,
            featureFlags: Map<String, Any?> = emptyMap(),
            frozenTimeIso: String? = null,
            randomSeed: Long? = null,
        ): DataEnvironment = DataEnvironment(
            databaseFile = databaseFile,
            debugSessionId = debugSessionId,
            featureFlags = featureFlags,
            frozenTimeIso = frozenTimeIso,
            randomSeed = randomSeed,
        )
    }
}
