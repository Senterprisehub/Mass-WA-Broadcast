package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri

data class DispatchIntentResult(
    val launched: Boolean,
    val tierUsed: String,
    val error: String? = null
)

object WhatsAppSender {

    /**
     * 4-Tier WhatsApp / Business / Clone Dispatch Router:
     * 1. Group / Name-Only Picker Route: ACTION_SEND (triggers WhatsApp "Send to..." picker searched by Accessibility)
     * 2. Media Route: ACTION_SEND + EXTRA_STREAM (FileProvider URI) + jid + sanitized caption (<= 1024 chars)
     * 3. Direct Deep Link: whatsapp://send?phone=...&text=...
     * 4. Web Fallback Chain: api.whatsapp.com -> wa.me
     *
     * Important OEM Dual-App Rule:
     * We ALWAYS set `setPackage(targetPackage)` first (for both Primary AND 2nd Clone!) so that on
     * Vivo (WhatsApp / II·WhatsApp), Xiaomi Dual Apps, Oppo/Realme App Cloner, and Samsung Dual Messenger,
     * the system chooser narrows strictly to the two instances of `targetPackage` rather than opening
     * a full 40-app system share sheet. If `setPackage` cannot resolve, we fall back cleanly without it.
     */
    fun dispatchMessage(
        context: Context,
        phoneNumber: String,
        message: String,
        isGroupTarget: Boolean,
        mediaPathOrUri: String?,
        targetPackage: String = WhatsAppInstanceDiscovery.PKG_WHATSAPP,
        isCloneTarget: Boolean = false
    ): DispatchIntentResult {
        val sanitizedCaption = MessageVariableParser.sanitizeMediaCaption(message, maxChars = 1024)
        val normalizedPhone = if (isGroupTarget) "" else (MessageVariableParser.normalizeIndianOrNriPhone(phoneNumber) ?: "")

        // Tier 1: Group Target (or WhatsApp-picked chat without phone number) -> ACTION_SEND into WhatsApp picker
        if (isGroupTarget || normalizedPhone.isBlank()) {
            if (!isGroupTarget && normalizedPhone.isBlank()) {
                // If it wasn't explicitly a group and has no valid phone number, only fail if there's no usable contact
                return DispatchIntentResult(false, "Validation", "Invalid recipient phone number")
            }
            val scopedIntent = buildGroupSendIntent(context, sanitizedCaption, mediaPathOrUri, targetPackage, applyPackage = true)
            if (tryStartActivity(context, scopedIntent)) {
                return DispatchIntentResult(true, "Tier1-GroupPicker")
            }
            val unscopedIntent = buildGroupSendIntent(context, sanitizedCaption, mediaPathOrUri, targetPackage, applyPackage = false)
            if (tryStartActivity(context, unscopedIntent)) {
                return DispatchIntentResult(true, "Tier1-GroupPicker-Unscoped")
            }
            return DispatchIntentResult(false, "Tier1-GroupPicker", "Unable to launch WhatsApp group picker")
        }

        // Tier 2: Media Route -> ACTION_SEND + FileProvider URI + jid + caption
        if (!mediaPathOrUri.isNullOrBlank()) {
            val shareUri = MediaAttachmentHelper.getShareableUri(context, mediaPathOrUri)
            if (shareUri != null) {
                val mimeType = MediaAttachmentHelper.resolveMimeType(context, mediaPathOrUri)
                val scopedMedia = buildMediaSendIntent(
                    shareUri = shareUri,
                    mimeType = mimeType,
                    sanitizedCaption = sanitizedCaption,
                    normalizedPhone = normalizedPhone,
                    targetPackage = targetPackage,
                    applyPackage = true
                )
                if (tryStartActivity(context, scopedMedia)) {
                    return DispatchIntentResult(true, "Tier2-MediaStream")
                }
                if (isCloneTarget) {
                    val unscopedMedia = buildMediaSendIntent(
                        shareUri = shareUri,
                        mimeType = mimeType,
                        sanitizedCaption = sanitizedCaption,
                        normalizedPhone = normalizedPhone,
                        targetPackage = targetPackage,
                        applyPackage = false
                    )
                    if (tryStartActivity(context, unscopedMedia)) {
                        return DispatchIntentResult(true, "Tier2-MediaStream-Unscoped")
                    }
                }
            }
        }

        val encodedText = Uri.encode(message)

        // Tier 3: Direct whatsapp:// deep link (scoped first so OEM chooser shows only Primary vs 2nd Clone)
        val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("whatsapp://send?phone=$normalizedPhone&text=$encodedText")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            setPackage(targetPackage)
        }
        if (tryStartActivity(context, deepLinkIntent)) {
            return DispatchIntentResult(true, "Tier3-DeepLink")
        }

        // Tier 4A: api.whatsapp.com fallback (scoped first)
        val apiWebIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedText")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            setPackage(targetPackage)
        }
        if (tryStartActivity(context, apiWebIntent)) {
            return DispatchIntentResult(true, "Tier4A-ApiWhatsappCom")
        }

        // Tier 4B: wa.me universal fallback (no package constraint)
        val waMeIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$normalizedPhone?text=$encodedText")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(context, waMeIntent)) {
            return DispatchIntentResult(true, "Tier4B-WaMe")
        }

        return DispatchIntentResult(false, "AllTiersFailed", "WhatsApp or WhatsApp Business is not installed on this device")
    }

    private fun buildMediaSendIntent(
        shareUri: Uri,
        mimeType: String,
        sanitizedCaption: String,
        normalizedPhone: String,
        targetPackage: String,
        applyPackage: Boolean
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            if (sanitizedCaption.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, sanitizedCaption)
            }
            putExtra("jid", "$normalizedPhone@s.whatsapp.net")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            if (applyPackage) {
                setPackage(targetPackage)
            }
        }
    }

    private fun buildGroupSendIntent(
        context: Context,
        message: String,
        mediaPathOrUri: String?,
        targetPackage: String,
        applyPackage: Boolean
    ): Intent {
        val shareUri = mediaPathOrUri?.takeIf { it.isNotBlank() }?.let {
            MediaAttachmentHelper.getShareableUri(context, it)
        }
        return Intent(Intent.ACTION_SEND).apply {
            if (shareUri != null) {
                type = MediaAttachmentHelper.resolveMimeType(context, mediaPathOrUri)
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            if (message.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, message)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (applyPackage) {
                setPackage(targetPackage)
            }
        }
    }

    fun launchWhatsAppForPicker(
        context: Context,
        targetPackage: String = WhatsAppInstanceDiscovery.PKG_WHATSAPP
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            ?: context.packageManager.getLaunchIntentForPackage(WhatsAppInstanceDiscovery.PKG_WHATSAPP)
            ?: context.packageManager.getLaunchIntentForPackage(WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStartActivity(context, launchIntent)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
