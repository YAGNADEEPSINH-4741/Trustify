package com.safecallai.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// QUIET PREMIUM — TRUSTIFY DESIGN SYSTEM COLOR TOKENS
// A disciplined, restrained palette combining Linear restraint with Material 3
// ============================================================================

// --- Dark Theme Tokens (Quiet Premium) ---
val DarkBackground = Color(0xFF0B0F14)
val DarkSurfacePrimary = Color(0xFF111820)
val DarkSurfaceSecondary = Color(0xFF171F29)
val DarkSurfaceElevated = Color(0xFF1D2733)

val DarkTextPrimary = Color(0xFFF4F7FA)
val DarkTextSecondary = Color(0xA5B0BEFF) // #A5B0BE
val DarkTextMuted = Color(0xFF758293)

val DarkBorder = Color(0xFF283442)
val DarkBorderSubtle = Color(0xFF1E2833)

val DarkAccentMint = Color(0xFF76E0B5)
val DarkAccentPressed = Color(0xFF56C99A)
val DarkAccentSubtleSurface = Color(0xFF17382F)

val DarkInfo = Color(0xFF79B8FF)
val DarkWarning = Color(0xFFF2C879)
val DarkError = Color(0xFFFF7777)
val DarkSuccess = Color(0xFF76E0B5)

// --- Light Theme Tokens ---
val LightBackground = Color(0xFFF6F8FA)
val LightSurfacePrimary = Color(0xFFFFFFFF)
val LightSurfaceSecondary = Color(0xFFEEF2F5)
val LightSurfaceElevated = Color(0xFFFFFFFF)

val LightTextPrimary = Color(0xFF17212B)
val LightTextSecondary = Color(0xFF526170)
val LightTextMuted = Color(0xFF718096)

val LightBorder = Color(0xFFDFE5EB)
val LightBorderSubtle = Color(0xFFE5E9EE)

val LightAccentMint = Color(0xFF147D5A)
val LightAccentPressed = Color(0xFF0F6347)
val LightAccentSubtleSurface = Color(0xFFDDF5E9)

val LightInfo = Color(0xFF0969DA)
val LightWarning = Color(0xFF9A6700)
val LightError = Color(0xFFCF222E)
val LightSuccess = Color(0xFF147D5A)

// --- Backward Compatibility Semantic Aliases ---
// Dark Palette Aliases
val Navy950 = DarkBackground
val Navy900 = DarkSurfacePrimary
val Navy850 = DarkSurfaceSecondary
val Navy800 = DarkSurfaceElevated
val Navy750 = DarkBorder
val Navy700 = DarkBorder
val Navy600 = DarkTextMuted

val Slate950 = DarkBackground
val Slate900 = DarkSurfacePrimary
val Slate850 = DarkSurfaceSecondary
val Slate800 = DarkSurfaceElevated
val Slate700 = DarkBorder
val Slate600 = DarkBorderSubtle
val Slate500 = DarkTextMuted
val Slate400 = DarkTextSecondary
val Slate200 = LightBorder
val Slate100 = DarkTextPrimary
val Slate50 = DarkTextPrimary

val ElectricMint = DarkAccentMint
val ElectricMintDark = DarkAccentPressed
val ElectricMintGlow = Color(0x2076E0B5)
val ElectricMintContainer = DarkAccentSubtleSurface

val CyanAccent = DarkInfo
val CyanAccentGlow = Color(0x2079B8FF)
val CyanAccentDark = Color(0xFF539BF5)

val BrandBlue = DarkInfo
val BrandBlueDark = Color(0xFF539BF5)
val BrandBlueLight = DarkInfo
val BrandCyan = DarkInfo

val LegitGreen = DarkSuccess
val LegitGreenDark = DarkAccentPressed
val LegitGreenContainer = DarkAccentSubtleSurface
val LegitGreenGlow = Color(0x2076E0B5)

val LikelyLegitTeal = DarkAccentMint
val LikelyLegitContainer = DarkAccentSubtleSurface

val UnknownGray = DarkTextMuted
val UnknownGrayDark = DarkBorder
val UnknownGrayContainer = DarkSurfaceSecondary

val SpamAmber = DarkWarning
val SpamAmberDark = Color(0xFFD99B26)
val SpamAmberContainer = Color(0xFF382B14)
val SpamAmberGlow = Color(0x20F2C879)

val HighRiskRed = DarkError
val HighRiskRedDark = Color(0xFFE55353)
val HighRiskRedContainer = Color(0xFF3B1818)
val HighRiskRedGlow = Color(0x20FF7777)

val GlassWhite10 = Color(0x1AFFFFFF)
val GlassWhite20 = Color(0x33FFFFFF)
val GlassWhite05 = Color(0x0DFFFFFF)
val GlassDark10 = Color(0x1A000000)
val GlassDark20 = Color(0x33000000)
