package com.floralens.domain.model

/**
 * Which identifier actually produced a given [IdResult] — distinct from
 * [IdentificationMode], which is the user's *preference*. When mode is
 * ONLINE but there's no connectivity, the hybrid identifier falls back to
 * on-device and the result still carries source = ON_DEVICE, so the UI
 * stays honest about what actually ran rather than what was requested.
 */
enum class IdentificationSource {
    ON_DEVICE,
    PLANTNET,
}