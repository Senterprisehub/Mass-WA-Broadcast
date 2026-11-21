package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class WhatsAppPickerMode(val label: String) {
    CONTACTS_AND_GROUPS("Contacts & Groups"),
    CONTACTS_ONLY("Contacts Only"),
    GROUPS_ONLY("WhatsApp Groups Only")
}

data class CapturedWhatsAppTarget(
    val name: String,
    val phoneNumber: String, // normalized E.164 for contacts, or "GROUP:<slug>" for groups
    val isGroup: Boolean,
    val capturedAt: Long = System.currentTimeMillis()
) {
    val dedupeKey: String
        get() = if (isGroup) "group:${name.trim().lowercase()}" else "phone:$phoneNumber"
}

data class WhatsAppPickerState(
    val isActive: Boolean = false,
    val mode: WhatsAppPickerMode = WhatsAppPickerMode.CONTACTS_AND_GROUPS,
    val targetGroupId: Long = 0L,
    val capturedTargets: List<CapturedWhatsAppTarget> = emptyList(),
    val lastCapturedLabel: String = ""
)

/**
 * Coordinates live multi-contact & multi-group selection directly inside WhatsApp / WhatsApp Business.
 * Stays active across multiple chat taps, deduplicates normalized contacts and groups in real time,
 * and surfaces the batch for one-tap import when the user returns to Mass WA Broadcast.
 */
object WhatsAppPickerCoordinator {

    private val _state = MutableStateFlow(WhatsAppPickerState())
    val state: StateFlow<WhatsAppPickerState> = _state.asStateFlow()

    fun startSession(mode: WhatsAppPickerMode, targetGroupId: Long = 0L) {
        _state.value = WhatsAppPickerState(
            isActive = true,
            mode = mode,
            targetGroupId = targetGroupId,
            capturedTargets = emptyList(),
            lastCapturedLabel = ""
        )
        WhatsAppAutomationController.onPickerSessionChanged(active = true)
    }

    fun recordCapturedContact(rawName: String, rawPhone: String?): Boolean {
        val current = _state.value
        if (!current.isActive) return false
        if (current.mode == WhatsAppPickerMode.GROUPS_ONLY) return false

        val normalizedPhone = MessageVariableParser.normalizeIndianOrNriPhone(rawPhone) ?: return false
        val cleanName = rawName.trim().ifBlank { MessageVariableParser.formatDisplayPhone(normalizedPhone) }
        val target = CapturedWhatsAppTarget(
            name = cleanName,
            phoneNumber = normalizedPhone,
            isGroup = false
        )
        return addIfUnique(target)
    }

    fun recordCapturedGroup(rawGroupName: String): Boolean {
        val current = _state.value
        if (!current.isActive) return false
        if (current.mode == WhatsAppPickerMode.CONTACTS_ONLY) return false

        val cleanName = rawGroupName.trim()
        if (cleanName.length < 2) return false
        val slug = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(32)
        val target = CapturedWhatsAppTarget(
            name = cleanName,
            phoneNumber = "GROUP:$slug",
            isGroup = true
        )
        return addIfUnique(target)
    }

    private fun addIfUnique(target: CapturedWhatsAppTarget): Boolean {
        var added = false
        _state.update { state ->
            if (state.capturedTargets.any { it.dedupeKey == target.dedupeKey }) {
                state
            } else {
                added = true
                state.copy(
                    capturedTargets = state.capturedTargets + target,
                    lastCapturedLabel = if (target.isGroup) "Group: ${target.name}" else target.name
                )
            }
        }
        return added
    }

    fun removeCaptured(dedupeKey: String) {
        _state.update { state ->
            state.copy(capturedTargets = state.capturedTargets.filterNot { it.dedupeKey == dedupeKey })
        }
    }

    fun consumeAndStopSession(): List<CapturedWhatsAppTarget> {
        val items = _state.value.capturedTargets
        _state.value = WhatsAppPickerState()
        WhatsAppAutomationController.onPickerSessionChanged(active = false)
        return items
    }

    fun cancelSession() {
        _state.value = WhatsAppPickerState()
        WhatsAppAutomationController.onPickerSessionChanged(active = false)
    }
}
