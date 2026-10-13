package com.mutissx.soundframe.domain.model

/**
 * Capabilities that may become premium in the future. Core creation (recording, analysis, all
 * five styles, saving and basic PNG export) intentionally has no entry here: it stays free.
 */
enum class PremiumFeature {
    ULTRA_RESOLUTION_EXPORT,
    VIDEO_EXPORT,
    BRANDING_REMOVAL,
}
