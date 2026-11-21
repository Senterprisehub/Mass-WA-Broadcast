package com.example

import android.graphics.Rect
import com.example.data.model.AccessibilityScopeMode
import com.example.util.WhatsAppAutoSendAccessibilityService
import com.example.util.WhatsAppAutomationController
import com.example.util.WhatsAppPickerCoordinator
import com.example.util.WhatsAppPickerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatsAppAccessibilityLogicTest {

    @Test
    fun `recognizes all 13 Pan-India Send labels and blocks Voice-Note and View-Once`() {
        val scripts = listOf(
            "Send", "भेजें", "सेंड करें", "पाठवा", "পাঠান", "ભેજો", "મોકલો",
            "ਭੇਜੋ", "ପଠାନ୍ତୁ", "పంపు", "அனுப்பு", "ಕಳುಹಿಸಿ", "അയയ്ക്കുക", "بھیجیں"
        )
        for (label in scripts) {
            assertTrue(
                "Expected '$label' to match Send button",
                WhatsAppAutoSendAccessibilityService.isSafeSendLabelOrId("", label, "", hasMedia = false)
            )
        }

        // Strict blockers
        assertFalse(
            WhatsAppAutoSendAccessibilityService.isSafeSendLabelOrId(
                "com.whatsapp:id/voice_note_send", "Send voice message", "", hasMedia = false
            )
        )
        assertFalse(
            WhatsAppAutoSendAccessibilityService.isSafeSendLabelOrId(
                "com.whatsapp:id/view_once_toggle", "View once", "", hasMedia = true
            )
        )
        assertFalse(
            WhatsAppAutoSendAccessibilityService.isSafeSendLabelOrId(
                "com.whatsapp:id/voice_note_btn", "Record voice message", "", hasMedia = false
            )
        )
    }

    @Test
    fun `guards hint placeholders, stale chat headers, and dialog-only invalid recipient alerts`() {
        assertEquals("", WhatsAppAutoSendAccessibilityService.extractNonHintEntryText("Message", "Message"))
        assertEquals("", WhatsAppAutoSendAccessibilityService.extractNonHintEntryText("संदेश", null))
        assertEquals("Hello", WhatsAppAutoSendAccessibilityService.extractNonHintEntryText("Hello", "Message"))

        // Stale chat header guard
        assertTrue(
            WhatsAppAutoSendAccessibilityService.doesOpenChatMatchRecipient(
                headerTitle = "Rohan Kulkarni",
                expectedName = "Rohan Kulkarni",
                expectedPhone = "919876543210",
                previousName = "Ananya Das",
                previousPhone = "919111222333"
            )
        )
        assertFalse(
            WhatsAppAutoSendAccessibilityService.doesOpenChatMatchRecipient(
                headerTitle = "Ananya Das",
                expectedName = "Rohan Kulkarni",
                expectedPhone = "919876543210",
                previousName = "Ananya Das",
                previousPhone = "919111222333"
            )
        )

        // Dialog-only invalid detection (ignores message bubble copy containing "invalid")
        assertNull(
            WhatsAppAutoSendAccessibilityService.classifyInvalidRecipientDialog(
                isAlertDialogContext = false,
                dialogTexts = listOf("Your promo code is invalid after Friday")
            )
        )
        assertNotNull(
            WhatsAppAutoSendAccessibilityService.classifyInvalidRecipientDialog(
                isAlertDialogContext = true,
                dialogTexts = listOf("The phone number +91 90000 00000 isn't on WhatsApp.")
            )
        )
    }

    @Test
    fun `dynamic package scoping isolates banking apps and restricts systemui strictly to lockscreen unlock`() {
        val ownPkg = "com.aistudio.masswabroadcast.mwbxkp"

        val idle = WhatsAppAutomationController.resolveAllowedPackages(AccessibilityScopeMode.IDLE_ISOLATED, ownPkg)
        assertEquals(1, idle.size)
        assertEquals(ownPkg, idle[0])

        val whatsappOnly = WhatsAppAutomationController.resolveAllowedPackages(AccessibilityScopeMode.SCOPED_WHATSAPP, ownPkg)
        assertTrue("com.whatsapp" in whatsappOnly)
        assertTrue("com.whatsapp.w4b" in whatsappOnly)
        assertFalse("com.android.systemui" in whatsappOnly)
        assertFalse("com.google.android.apps.nbu.paisa.user" in whatsappOnly)

        val lockScope = WhatsAppAutomationController.resolveAllowedPackages(AccessibilityScopeMode.SCOPED_LOCK_UNLOCK, ownPkg)
        assertTrue("com.android.systemui" in lockScope)
    }

    @Test
    fun `computes exact 3x3 pattern unlock node centers and deduplicates WhatsApp picker batches`() {
        val centers = WhatsAppAutoSendAccessibilityService.computePatternNodeCenters(Rect(0, 0, 300, 300))
        assertEquals(9, centers.size)
        assertEquals(50f, centers[0].x, 0.1f)
        assertEquals(50f, centers[0].y, 0.1f)
        assertEquals(150f, centers[4].x, 0.1f)
        assertEquals(150f, centers[4].y, 0.1f)
        assertEquals(250f, centers[8].x, 0.1f)
        assertEquals(250f, centers[8].y, 0.1f)

        // Live multi-contact & group picker deduplication
        WhatsAppPickerCoordinator.startSession(WhatsAppPickerMode.CONTACTS_AND_GROUPS, targetGroupId = 1L)
        assertTrue(WhatsAppPickerCoordinator.recordCapturedContact("Suresh", "9876543210"))
        assertFalse(WhatsAppPickerCoordinator.recordCapturedContact("Suresh Duplicate", "+91 98765 43210"))
        assertTrue(WhatsAppPickerCoordinator.recordCapturedGroup("Mumbai Retailers Hub"))
        assertFalse(WhatsAppPickerCoordinator.recordCapturedGroup("mumbai retailers hub"))

        val batch = WhatsAppPickerCoordinator.consumeAndStopSession()
        assertEquals(2, batch.size)
        assertFalse(batch[0].isGroup)
        assertTrue(batch[1].isGroup)
    }

    @Test
    fun `matches Indian OEM lockscreen PIN keypad digit nodes accurately`() {
        assertTrue(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("com.android.systemui:id/key5", "", "", '5'))
        assertTrue(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("com.android.systemui:id/digit_7", "", "", '7'))
        assertTrue(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("com.samsung.android.systemui:id/num_0", "", "", '0'))
        assertTrue(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("com.oplus.systemui:id/btn_9", "", "", '9'))
        assertTrue(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("", "3", "", '3'))
        assertTrue(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("", "", "8", '8'))

        // Non-matches must be rejected
        assertFalse(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("com.android.systemui:id/key6", "6", "6", '5'))
        assertFalse(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("com.android.systemui:id/key_enter", "OK", "OK", '5'))
        assertFalse(WhatsAppAutoSendAccessibilityService.matchesKeypadDigit("", "15", "15", '5'))
    }

    @Test
    fun `chooser matcher selects exact Primary vs 2nd Clone across Vivo II-WhatsApp and Indian OEMs`() {
        val vivoSheet = listOfNotNull(
            com.example.util.WhatsAppChooserAndScreenMatcher.buildCandidate("WhatsApp"),
            com.example.util.WhatsAppChooserAndScreenMatcher.buildCandidate("II·WhatsApp")
        )
        assertEquals(2, vivoSheet.size)
        assertFalse(vivoSheet[0].isClone)
        assertTrue(vivoSheet[1].isClone)

        // Primary chosen -> index 0 ("WhatsApp")
        assertEquals(
            0,
            com.example.util.WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(
                candidates = vivoSheet,
                isBusinessTarget = false,
                isCloneTarget = false
            )
        )
        // 2nd Clone chosen -> index 1 ("II·WhatsApp")
        assertEquals(
            1,
            com.example.util.WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(
                candidates = vivoSheet,
                isBusinessTarget = false,
                isCloneTarget = true
            )
        )

        // Identical labels with graphical badge only -> fallback to index 1 for Clone, index 0 for Primary
        val badgeOnlySheet = listOfNotNull(
            com.example.util.WhatsAppChooserAndScreenMatcher.buildCandidate("WhatsApp"),
            com.example.util.WhatsAppChooserAndScreenMatcher.buildCandidate("WhatsApp")
        )
        assertEquals(0, com.example.util.WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(badgeOnlySheet, false, false))
        assertEquals(1, com.example.util.WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(badgeOnlySheet, false, true))

        // Recognizes Android 13-16 IntentResolver and Vivo/MIUI/ColorOS chooser windows
        assertTrue(
            com.example.util.WhatsAppChooserAndScreenMatcher.looksLikeChooserWindow(
                "com.android.intentresolver",
                "complete action using whatsapp ii·whatsapp"
            )
        )
        assertFalse(
            com.example.util.WhatsAppChooserAndScreenMatcher.looksLikeChooserWindow(
                "com.whatsapp",
                "complete action using"
            )
        )

        // Scope allowlist includes IntentResolver + Vivo AppClone while keeping idle strictly isolated
        val activeScope = WhatsAppAutomationController.resolveAllowedPackages(
            AccessibilityScopeMode.SCOPED_WHATSAPP,
            "com.aistudio.masswabroadcast.mwbxkp"
        )
        assertTrue("com.android.intentresolver" in activeScope)
        assertTrue("com.vivo.appclone" in activeScope)
    }

    @Test
    fun `screen classifier separates ContactPicker, MediaPreview, and Conversation cleanly`() {
        assertEquals(
            com.example.util.WhatsAppScreenKind.CONTACT_PICKER,
            com.example.util.WhatsAppChooserAndScreenMatcher.classifyWhatsAppScreen(
                hasConversationHeader = false,
                hasEntryBox = false,
                hasCaptionBox = false,
                hasMediaPreviewControls = false,
                hasContactPickerSignals = true,
                hasMediaTask = true
            )
        )
        assertEquals(
            com.example.util.WhatsAppScreenKind.MEDIA_PREVIEW,
            com.example.util.WhatsAppChooserAndScreenMatcher.classifyWhatsAppScreen(
                hasConversationHeader = false,
                hasEntryBox = false,
                hasCaptionBox = true,
                hasMediaPreviewControls = true,
                hasContactPickerSignals = false,
                hasMediaTask = true
            )
        )
        assertEquals(
            com.example.util.WhatsAppScreenKind.CONVERSATION,
            com.example.util.WhatsAppChooserAndScreenMatcher.classifyWhatsAppScreen(
                hasConversationHeader = true,
                hasEntryBox = true,
                hasCaptionBox = false,
                hasMediaPreviewControls = false,
                hasContactPickerSignals = false,
                hasMediaTask = false
            )
        )

        // Always expose Primary + 2nd Clone options when WhatsApp is detected
        val expanded = com.example.util.WhatsAppInstanceDiscovery.ensurePrimaryAndCloneOptions(
            listOf(
                com.example.data.model.WhatsAppInstance(
                    packageName = com.example.util.WhatsAppInstanceDiscovery.PKG_WHATSAPP,
                    label = "WhatsApp (Primary)",
                    userSerial = 0L,
                    isBusiness = false,
                    isClone = false
                )
            )
        )
        assertEquals(2, expanded.size)
        assertFalse(expanded[0].isClone)
        assertTrue(expanded[1].isClone)
    }
}
