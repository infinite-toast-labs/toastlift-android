package dev.toastlabs.toastlift.data

import org.json.JSONObject

internal fun defaultWorkUnitValues(workUnits: List<WorkUnitDefinition>): Map<String, String> =
    workUnits.associate { unit ->
        unit.key to unit.defaultValue.orEmpty()
    }

internal fun encodeWorkUnitValues(values: Map<String, String>): String? {
    if (values.isEmpty()) return null
    val normalized = values
        .mapValues { (_, value) -> value.trim() }
        .filterValues { it.isNotEmpty() }
    if (normalized.isEmpty()) return null
    val json = JSONObject()
    normalized.toSortedMap().forEach { (key, value) ->
        json.put(key, value)
    }
    return json.toString()
}

internal fun decodeWorkUnitValues(rawValue: String?): Map<String, String> {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isEmpty() || raw == "{}") return emptyMap()
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
    return buildMap {
        json.keys().forEach { key ->
            val value = json.optString(key).trim()
            if (key.isNotBlank() && value.isNotEmpty()) {
                put(key, value)
            }
        }
    }
}

internal fun SessionSet.hasLoggedWorkUnitSignal(): Boolean =
    completed && encodeWorkUnitValues(workUnitValues) != null
