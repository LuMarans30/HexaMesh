package com.lumarans30.hexamesh.platform

/** Minimal preference seam so settings logic stays testable without Android. */
interface SettingsStore {
    fun getString(
        key: String,
        defaultValue: String,
    ): String

    fun putString(
        key: String,
        value: String,
    )
}
