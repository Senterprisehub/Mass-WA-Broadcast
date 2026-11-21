package com.example.util

/**
 * Pure, deterministic classification helpers for Indian OEM Dual-App / Clone choosers
 * (Vivo/iQOO II·WhatsApp, Xiaomi Dual Apps, Oppo/Realme/OnePlus App Cloner,
 * Samsung Dual Messenger, Tecno/Infinix/Itel App Twin) and WhatsApp multi-step screens
 * (ContactPicker vs MediaPreview vs Conversation vs MainChats).
 */
enum class WhatsAppScreenKind {
    CONTACT_PICKER,
    MEDIA_PREVIEW,
    CONVERSATION,
    MAIN_CHATS,
    OTHER
}

data class ChooserCandidate(
    val label: String,
    val contentDescription: String = "",
    val isBusiness: Boolean = false,
    val isClone: Boolean = false
)

object WhatsAppChooserAndScreenMatcher {

    private val CHOOSER_PACKAGES = setOf(
        "android",
        "com.android.intentresolver",
        "com.vivo.doubleinstance",
        "com.vivo.appclone",
        "com.vivo.daemonservice",
        "com.bbk.launcher2",
        "com.miui.securitycore",
        "com.miui.securitycenter",
        "com.miui.home",
        "com.xiaomi.xmsf",
        "com.oplus.multiapp",
        "com.oplus.exsystemservice",
        "com.coloros.safecenter",
        "com.samsung.android.da.daagent",
        "com.transsion.phonemaster",
        "com.transsion.appclone",
        "com.transsion.hilauncher",
        "com.huawei.android.launcher"
    )

    private val CHOOSER_PROMPT_PHRASES = listOf(
        "complete action using",
        "open with",
        "share with",
        "choose an app",
        "select an app",
        "select app",
        "use a different app",
        "just once",
        "dual app",
        "app clone",
        "app twin"
    )

    fun looksLikeChooserWindow(packageName: String, windowTextsLower: String): Boolean {
        val pkgLower = packageName.lowercase()
        if (pkgLower in WhatsAppInstanceDiscovery.OFFICIAL_WHATSAPP_PACKAGES) {
            return false
        }
        val hasPrompt = CHOOSER_PROMPT_PHRASES.any { windowTextsLower.contains(it) }
        if (hasPrompt) return true
        return pkgLower in CHOOSER_PACKAGES && windowTextsLower.contains("whatsapp")
    }

    /**
     * Identifies cloned/dual WhatsApp labels across all Indian OEMs:
     * - Vivo / iQOO Funtouch OS: "II·WhatsApp", "II.WhatsApp", "Ⅱ·WhatsApp", "2nd"
     * - Xiaomi MIUI / HyperOS: "Dual WhatsApp", "WhatsApp (Dual)", "WhatsApp 2"
     * - Oppo / Realme / OnePlus: "WhatsApp (Clone)", "WhatsApp(Clone)", "Parallel WhatsApp", "Multi WhatsApp"
     * - Samsung One UI: "WhatsApp (Dual Messenger)", "Secondary", "Work WhatsApp"
     * - Tecno / Infinix / Itel (Transsion HiOS / XOS): "WhatsApp Twin", "Twin WhatsApp"
     */
    fun isCloneAppLabel(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()

        // Vivo / iQOO Roman numeral II prefix (ASCII "II" or Unicode Roman numeral "Ⅱ")
        if (trimmed.startsWith("II·") ||
            trimmed.startsWith("II.") ||
            trimmed.startsWith("II ") ||
            trimmed.startsWith("II-") ||
            trimmed.startsWith("Ⅱ") ||
            lower.contains("ii·whatsapp") ||
            lower.contains("ii.whatsapp") ||
            lower.contains("ⅱ·whatsapp")
        ) {
            return true
        }

        if (lower.contains("2nd") ||
            lower.contains("dual") ||
            lower.contains("clone") ||
            lower.contains("twin") ||
            lower.contains("parallel") ||
            lower.contains("second") ||
            lower.contains("secondary") ||
            lower.contains("work") ||
            lower.contains("multi")
        ) {
            return true
        }

        // Standalone digit "2" token (e.g. "WhatsApp 2" or "WhatsApp (2)")
        val trailingTwoRegex = Regex("""(^|[\s(\[-])2([\s)\]-]|$)""")
        return trailingTwoRegex.containsMatchIn(lower)
    }

    fun buildCandidate(label: String, contentDescription: String = ""): ChooserCandidate? {
        val combined = "$label $contentDescription".trim()
        val lower = combined.lowercase()
        if (!lower.contains("whatsapp")) return null
        val isBusiness = lower.contains("business") || lower.contains("w4b")
        val isClone = isCloneAppLabel(label) || isCloneAppLabel(contentDescription)
        return ChooserCandidate(
            label = label.trim(),
            contentDescription = contentDescription.trim(),
            isBusiness = isBusiness,
            isClone = isClone
        )
    }

    /**
     * Selects the exact index from [candidates] matching (isBusinessTarget, isCloneTarget).
     * Returns -1 if no candidate matches.
     */
    fun selectChooserCandidateIndex(
        candidates: List<ChooserCandidate>,
        isBusinessTarget: Boolean,
        isCloneTarget: Boolean
    ): Int {
        if (candidates.isEmpty()) return -1

        val flavorIndices = candidates.indices.filter { candidates[it].isBusiness == isBusinessTarget }
        val pool = if (flavorIndices.isNotEmpty()) flavorIndices else candidates.indices.toList()

        return if (isCloneTarget) {
            // 1. Prefer explicitly labeled clone ("II·WhatsApp", "WhatsApp Twin", "Dual WhatsApp", "WhatsApp (Clone)", etc.)
            pool.firstOrNull { candidates[it].isClone }
                // 2. Fallback: if OEM shows two identical "WhatsApp" text labels with graphical badge only, pick the 2nd tile
                ?: pool.getOrNull(1)
                ?: pool.first()
        } else {
            // Primary target: strictly prefer the non-clone tile ("WhatsApp", never "II·WhatsApp" / "WhatsApp Twin")
            pool.firstOrNull { !candidates[it].isClone }
                ?: pool.first()
        }
    }

    /**
     * Unambiguously separates WhatsApp's "Send to..." ContactPicker, MediaComposerActivity,
     * Conversation screen, and cold-start Main Chats screen so a picker Next FAB is never
     * mistaken for final message delivery and a cold-start Chats tab can recover automatically.
     */
    fun classifyWhatsAppScreen(
        hasConversationHeader: Boolean,
        hasEntryBox: Boolean,
        hasCaptionBox: Boolean,
        hasMediaPreviewControls: Boolean,
        hasContactPickerSignals: Boolean,
        hasMediaTask: Boolean,
        hasMainChatsSignals: Boolean = false
    ): WhatsAppScreenKind {
        if (hasMediaTask && (hasCaptionBox || hasMediaPreviewControls)) {
            return WhatsAppScreenKind.MEDIA_PREVIEW
        }
        if (hasContactPickerSignals && !hasConversationHeader && !hasEntryBox) {
            return WhatsAppScreenKind.CONTACT_PICKER
        }
        if (hasConversationHeader || hasEntryBox) {
            return WhatsAppScreenKind.CONVERSATION
        }
        if (hasCaptionBox || hasMediaPreviewControls) {
            return WhatsAppScreenKind.MEDIA_PREVIEW
        }
        if (hasContactPickerSignals) {
            return WhatsAppScreenKind.CONTACT_PICKER
        }
        if (hasMainChatsSignals) {
            return WhatsAppScreenKind.MAIN_CHATS
        }
        return WhatsAppScreenKind.OTHER
    }
}
