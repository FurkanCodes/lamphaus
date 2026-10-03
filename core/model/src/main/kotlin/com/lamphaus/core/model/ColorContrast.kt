package com.lamphaus.core.model

import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2.x minimum for normal text (MOB-CLR-06). */
const val TEXT_CONTRAST = 4.5

/** WCAG 2.x minimum for large text, icons, and control boundaries (MOB-CLR-06). */
const val NON_TEXT_CONTRAST = 3.0

/** Relative luminance of an opaque ARGB color (WCAG 2.x). */
fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG contrast ratio between two opaque ARGB colors, 1.0 to 21.0. */
fun contrastRatio(first: Int, second: Int): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/**
 * [argb] moved toward white (on a dark [background]) or black (on a light one)
 * in small steps until it reaches [minRatio] against [background]. Content
 * accents pass through this before they color text, controls, or focus
 * (SHR-PROD-08); the hue survives, only the tone shifts.
 */
fun clampContrast(argb: Int, background: Int, minRatio: Double = TEXT_CONTRAST): Int {
    if (contrastRatio(argb, background) >= minRatio) return argb or OPAQUE
    val target = if (relativeLuminance(background) < 0.5) 0xFFFFFFFF.toInt() else OPAQUE
    for (step in 1..STEPS) {
        val candidate = mix(argb, target, step / STEPS.toDouble())
        if (contrastRatio(candidate, background) >= minRatio) return candidate
    }
    return target
}

private const val OPAQUE = 0xFF000000.toInt()
private const val STEPS = 40

private fun mix(from: Int, to: Int, amount: Double): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
    }
    return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
