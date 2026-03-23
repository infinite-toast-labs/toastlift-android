package dev.toastlabs.toastlift.ui

internal fun equipmentSelectionOptions(options: List<String>): List<String> {
    return options
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
}
