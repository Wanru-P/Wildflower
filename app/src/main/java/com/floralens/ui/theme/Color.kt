package com.floralens.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Wildflower Editorial semantic palette. The original exported names are
 * retained for legacy screens; warm amber remains a care-status signal.
 */

// ── Light scheme ──────────────────────────────────────────────
val Primary = Color(0xFF24452E)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFDCE6D6)
val OnPrimaryContainer = Color(0xFF00210F)

val Secondary = Color(0xFF52664F)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD5E8CE)
val OnSecondaryContainer = Color(0xFF101F0E)

val Tertiary = Color(0xFFE8A33D)          // amber — "needs water" accent
val OnTertiary = Color(0xFF452B00)
val TertiaryContainer = Color(0xFFFFDFA6)
val OnTertiaryContainer = Color(0xFF2C1B00)

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

val Background = Color(0xFFFAF9F3)
val OnBackground = Color(0xFF32372F)

val Surface = Color(0xFFFAF9F3)
val OnSurface = Color(0xFF32372F)
val SurfaceDim = Color(0xFFD9DBD3)
val SurfaceBright = Color(0xFFFFFEF8)
val SurfaceVariant = Color(0xFFDCE6D6)
val OnSurfaceVariant = Color(0xFF43483E)

val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF3F2E9)
val SurfaceContainer = Color(0xFFEDEEE4)
val SurfaceContainerHigh = Color(0xFFE5E8DF)
val SurfaceContainerHighest = Color(0xFFDFE2D9)

val Outline = Color(0xFF73796D)
val OutlineVariant = Color(0xFFC3C8BB)

val Scrim = Color(0xFF000000)
val InverseSurface = Color(0xFF2E312B)
val InverseOnSurface = Color(0xFFEFF2E9)
val InversePrimary = Color(0xFFA8D5B4)

// ── Dark scheme (used on the camera / Identify screen) ───────
val PrimaryDark = Color(0xFFA8D5B4)
val OnPrimaryDark = Color(0xFF0A3921)
val PrimaryContainerDark = Color(0xFF023822)
val OnPrimaryContainerDark = Color(0xFFC3E8CE)

val SecondaryDark = Color(0xFFB9CCB2)
val OnSecondaryDark = Color(0xFF253423)
val SecondaryContainerDark = Color(0xFF3B4B38)
val OnSecondaryContainerDark = Color(0xFFD5E8CE)

val TertiaryDark = Color(0xFFFFB95C)
val OnTertiaryDark = Color(0xFF482900)
val TertiaryContainerDark = Color(0xFF653D00)
val OnTertiaryContainerDark = Color(0xFFFFDFA6)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF12140F)
val OnBackgroundDark = Color(0xFFE2E3DA)

val SurfaceDark = Color(0xFF12140F)
val OnSurfaceDark = Color(0xFFE2E3DA)
val SurfaceDimDark = Color(0xFF12140F)
val SurfaceBrightDark = Color(0xFF383A33)
val SurfaceVariantDark = Color(0xFF43483E)
val OnSurfaceVariantDark = Color(0xFFC3C8BB)

val SurfaceContainerLowestDark = Color(0xFF0C0F0A)
val SurfaceContainerLowDark = Color(0xFF1A1C18)
val SurfaceContainerDark = Color(0xFF1E211C)
val SurfaceContainerHighDark = Color(0xFF282B26)
val SurfaceContainerHighestDark = Color(0xFF333630)

val OutlineDark = Color(0xFF8D9387)
val OutlineVariantDark = Color(0xFF43483E)

val InverseSurfaceDark = Color(0xFFE2E3DA)
val InverseOnSurfaceDark = Color(0xFF2E312B)
val InversePrimaryDark = Color(0xFF1B4332)

// ── Care-status semantics (chips) ─────────────────────────────
// Water   → tertiary-container tones
// Healthy → secondary-container tones
// Overdue → error-container tones
// Resolved through MaterialTheme.colorScheme so they follow light/dark automatically.
