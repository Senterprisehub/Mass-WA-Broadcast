package com.example

import com.example.data.model.Contact
import com.example.util.MessageVariableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianPhoneNormalizationTest {

    @Test
    fun `normalizes domestic 10 digit and leading zero Indian numbers`() {
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("9876543210"))
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("09876543210"))
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("00919876543210"))
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("+91 09876543210"))
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("+91-98765-43210"))
    }

    @Test
    fun `protects domestic 10 digit numbers starting with 91`() {
        assertEquals("919123456789", MessageVariableParser.normalizeIndianOrNriPhone("9123456789"))
    }

    @Test
    fun `normalizes Devanagari and Bengali numerals and strips Bidi markers`() {
        // Devanagari 9876543210 with LRM/RLM Bidi markers
        val devanagari = "\u200E+९१ ९८७६५ ४३२१०\u202C"
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone(devanagari))

        // Bengali 9876543210
        val bengali = "৯৮৭৬৫৪৩২১০"
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone(bengali))
    }

    @Test
    fun `preserves explicit NRI international prefixes and rejects emergency shortcodes`() {
        assertEquals("971501234567", MessageVariableParser.normalizeIndianOrNriPhone("+971 50 123 4567"))
        assertEquals("14155550199", MessageVariableParser.normalizeIndianOrNriPhone("+1 (415) 555-0199"))
        assertEquals("447700900123", MessageVariableParser.normalizeIndianOrNriPhone("+44 7700 900123"))
        assertEquals("6591234567", MessageVariableParser.normalizeIndianOrNriPhone("+65 9123 4567"))

        assertNull(MessageVariableParser.normalizeIndianOrNriPhone("100"))
        assertNull(MessageVariableParser.normalizeIndianOrNriPhone("112"))
        assertNull(MessageVariableParser.normalizeIndianOrNriPhone("108"))
        assertNull(MessageVariableParser.normalizeIndianOrNriPhone("123"))
    }

    @Test
    fun `resolves template variables and sanitizes long media captions`() {
        val contact = Contact(
            name = "Priya Verma",
            phoneNumber = "919876543210",
            company = "Verma Electronics",
            customTag = "DiwaliVIP"
        )
        val resolved = MessageVariableParser.resolveTemplate(
            "Namaste {{first_name}} ({{name}}) from {{company}} [{{custom_tag}}]",
            contact
        )
        assertEquals("Namaste Priya (Priya Verma) from Verma Electronics [DiwaliVIP]", resolved)

        val oversized = "A".repeat(1500)
        val sanitized = MessageVariableParser.sanitizeMediaCaption(oversized, maxChars = 1024)
        assertEquals(1024, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
    }
}
