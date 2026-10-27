package com.floralens.domain.model

/**
 * User's identification preference, chosen at onboarding and changeable
 * later from About & Help. Persisted via SettingsRepository.
 *
 * ONLINE is the default, disclosed at onboarding. OFFLINE keeps recognition
 * on the device. ONLINE uses Pl@ntNet for wider species coverage, with an
 * automatic, visible fallback to on-device identification when there's no
 * connectivity.
 */
enum class IdentificationMode {
    OFFLINE,
    ONLINE;

    companion object {
        val DEFAULT = ONLINE

        /** A saved explicit choice always wins over the first-install default. */
        fun fromStored(value: String?): IdentificationMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
