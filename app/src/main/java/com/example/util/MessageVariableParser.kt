package com.example.util

import com.example.data.model.Contact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageVariableParser {

    val SUPPORTED_VARIABLES = listOf(
        "{{name}}",
        "{{first_name}}",
        "{{company}}",
        "{{phone}}",
        "{{custom_tag}}",
        "{{date}}"
    )

    private val EMERGENCY_SHORTCODES = setOf(
        "100", "101", "102", "108", "112", "181", "1090", "1091", "1098", "1930", "911", "999"
    )

    fun resolveTemplate(template: String, contact: Contact, nowEpoch: Long = System.currentTimeMillis()): String {
        val trimmedName = contact.name.trim().ifBlank { "Friend" }
        val firstName = trimmedName.substringBefore(" ").ifBlank { trimmedName }
        val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(nowEpoch))

        return template
            .replace("{{name}}", trimmedName, ignoreCase = true)
            .replace("{{first_name}}", firstName, ignoreCase = true)
            .replace("{{company}}", contact.company.trim().ifBlank { "your team" }, ignoreCase = true)
            .replace("{{phone}}", contact.phoneNumber.trim(), ignoreCase = true)
            .replace("{{custom_tag}}", contact.customTag.trim(), ignoreCase = true)
            .replace("{{date}}", formattedDate, ignoreCase = true)
    }

    /**
     * Pan-India & NRI E.164 phone normalizer.
     * - Converts regional Unicode digits (Devanagari ०-९, Bengali ০-৯, Arabic-Indic, Gujarati, etc.) to ASCII
     * - Strips Bidi & zero-width formatting markers (\u200E, \u200F, \u202A..\u202E, \uFEFF)
     * - Handles leading trunk 0 (09876543210 -> 919876543210), 0091..., +91 09876543210 typos
     * - Protects 10-digit Indian mobile numbers starting with 91 (9123456789 -> 919123456789)
     * - Preserves explicit + NRI international prefixes (+971, +1, +44, +65)
     * - Rejects emergency shortcodes and invalid numbers (< 7 or > 15 digits)
     *
     * Returns digits-only E.164 (e.g. "919876543210") suitable for WhatsApp deep links, or null if invalid.
     */
    fun normalizeIndianOrNriPhone(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null

        // Strip invisible Unicode Bidi / formatting controls
        val cleaned = buildString(rawInput.length) {
            for (ch in rawInput) {
                val type = Character.getType(ch)
                val isBidiOrFormat = type == Character.FORMAT.toInt() ||
                    ch == '\u200E' || ch == '\u200F' || ch == '\uFEFF' ||
                    ch in '\u202A'..'\u202E' || ch in '\u2066'..'\u2069'
                if (!isBidiOrFormat) {
                    val numericVal = Character.getNumericValue(ch)
                    if (ch.isDigit() && numericVal in 0..9) {
                        append(('0'.code + numericVal).toChar())
                    } else {
                        append(ch)
                    }
                }
            }
        }.trim()

        if (cleaned.isEmpty()) return null

        val hasExplicitPlus = cleaned.startsWith("+")
        val hasInternationalDoubleZero = cleaned.startsWith("00")
        val digitsOnly = cleaned.filter { it.isDigit() }

        if (digitsOnly.isEmpty() || digitsOnly in EMERGENCY_SHORTCODES) return null

        val normalized = when {
            // Explicit +91 09876543210 typo (13 digits starting with 910)
            hasExplicitPlus && digitsOnly.startsWith("910") && digitsOnly.length == 13 -> {
                "91" + digitsOnly.substring(3)
            }
            // Explicit + international number (NRI e.g. +971..., +1..., +44..., +65...)
            hasExplicitPlus -> digitsOnly

            // 0091... or 00910...
            hasInternationalDoubleZero && digitsOnly.startsWith("00910") && digitsOnly.length == 15 -> {
                "91" + digitsOnly.substring(5)
            }
            hasInternationalDoubleZero -> digitsOnly.drop(2)

            // 10-digit domestic Indian number (including those starting with 91, e.g. 9123456789)
            digitsOnly.length == 10 && digitsOnly[0] in '6'..'9' -> "91$digitsOnly"

            // 11-digit Indian number with leading trunk zero (e.g. 09876543210)
            digitsOnly.length == 11 && digitsOnly.startsWith("0") && digitsOnly[1] in '6'..'9' -> {
                "91" + digitsOnly.substring(1)
            }

            // 12-digit Indian number already prefixed with 91
            digitsOnly.length == 12 && digitsOnly.startsWith("91") && digitsOnly[2] in '6'..'9' -> {
                digitsOnly
            }

            // 13-digit Indian typo 9109876543210 without +
            digitsOnly.length == 13 && digitsOnly.startsWith("910") && digitsOnly[3] in '6'..'9' -> {
                "91" + digitsOnly.substring(3)
            }

            // Other international numbers without '+'
            digitsOnly.length in 8..15 && !digitsOnly.startsWith("0") -> digitsOnly

            else -> return null
        }

        return if (normalized.length in 8..15 && normalized !in EMERGENCY_SHORTCODES) {
            normalized
        } else {
            null
        }
    }

    fun formatDisplayPhone(normalizedDigits: String): String {
        if (normalizedDigits.isBlank()) return ""
        if (normalizedDigits.startsWith("91") && normalizedDigits.length == 12) {
            return "+91 ${normalizedDigits.substring(2, 7)} ${normalizedDigits.substring(7)}"
        }
        return if (normalizedDigits.startsWith("+")) normalizedDigits else "+$normalizedDigits"
    }

    fun sanitizeMediaCaption(rawCaption: String, maxChars: Int = 1024): String {
        val trimmed = rawCaption.trim()
        return if (trimmed.length <= maxChars) trimmed else trimmed.take(maxChars - 1) + "…"
    }
}
