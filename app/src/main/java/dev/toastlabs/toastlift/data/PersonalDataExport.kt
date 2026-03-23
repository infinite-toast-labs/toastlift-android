package dev.toastlabs.toastlift.data

data class PersonalDataExportPayload(
    val fileName: String,
    val contents: String,
)
