package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class LockType(val label: String) {
    NONE("None"),
    SLIDE("Slide Lock"),
    PIN("PIN"),
    PATTERN("3x3 Pattern"),
    PASSWORD("Password")
}

enum class CampaignStatus(val label: String) {
    DRAFT("Draft"),
    SCHEDULED("Scheduled"),
    QUEUED("Queued"),
    RUNNING("Running"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

enum class DeliveryStatus(val label: String) {
    PENDING("Pending"),
    SENT("Sent"),
    DELIVERED("Delivered"),
    READ("Read"),
    FAILED("Failed"),
    SKIPPED("Skipped")
}

enum class AccessibilityScopeMode(val label: String) {
    DISABLED("Disabled"),
    IDLE_ISOLATED("Isolated (Own App Only)"),
    SCOPED_WHATSAPP("Scoped (WhatsApp Only)"),
    SCOPED_LOCK_UNLOCK("Scoped (WhatsApp + Lockscreen)")
}

enum class AutomationStep(val label: String) {
    IDLE("Idle"),
    SWIPING_LOCKSCREEN("Swiping Cover Lockscreen"),
    UNLOCKING_KEYGUARD("Unlocking Device"),
    UNLOCKING_WHATSAPP_LOCK("Unlocking WhatsApp Lock"),
    AWAITING_DISPATCH("Preparing Dispatch"),
    CHOOSER_SELECTION("Selecting WhatsApp Instance"),
    SEARCHING_RECIPIENT("Locating Chat / Group"),
    OPENING_CHAT("Opening Conversation"),
    FILLING_CAPTION("Filling Media Caption"),
    PASTING_AND_SENDING("Sending Message"),
    SENDING_TEXT_FOLLOWUP("Sending Text Follow-Up"),
    CONFIRMING_SENT("Confirming Send"),
    RETURNING_TO_MAIN("Returning to App"),
    EXITING_WHATSAPP("Exiting WhatsApp to Home"),
    LOCKING_DEVICE("Auto-Locking Device"),
    RESTING("Anti-Ban Cooldown"),
    PAUSED("Paused")
}

enum class OemFamily(val displayName: String) {
    XIAOMI("Xiaomi / Redmi / POCO (MIUI & HyperOS)"),
    OPPO_REALME_ONEPLUS("Oppo / Realme / OnePlus (ColorOS & OxygenOS)"),
    VIVO_IQOO("Vivo / iQOO (Funtouch OS & OriginOS)"),
    SAMSUNG("Samsung (One UI)"),
    TRANSSION("Tecno / Infinix / Itel (HiOS & XOS)"),
    OTHER("Stock Android / Motorola / Pixel / Other")
}

enum class BootScheduleDecision {
    REARM_FUTURE,
    FIRE_IMMEDIATELY_WITHIN_GRACE,
    MARK_FAILED_MISSED_WINDOW
}

@Entity(tableName = "contact_groups")
data class ContactGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#4F46E5",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["phoneNumber"])
    ]
)
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long = 0,
    val name: String,
    val phoneNumber: String,
    val company: String = "",
    val customTag: String = "",
    val notes: String = "",
    val isOptedIn: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isGroup: Boolean = false
)

@Entity(
    tableName = "campaigns",
    indices = [
        Index(
            value = ["status", "scheduledTimeEpoch"],
            name = "index_campaigns_status_scheduledTimeEpoch"
        )
    ]
)
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val messageTemplate: String,
    val targetGroupIds: String = "",
    val scheduledTimeEpoch: Long = 0L,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val totalRecipients: Int = 0,
    val sentCount: Int = 0,
    val deliveredCount: Int = 0,
    val readCount: Int = 0,
    val failedCount: Int = 0,
    val delaySeconds: Int = 6,
    val mediaUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val targetPackage: String = "com.whatsapp",
    val targetUserSerial: Long = 0L,
    val isCloneTarget: Boolean = false,
    val failureDiagnostic: String? = null
)

@Entity(
    tableName = "broadcast_logs",
    indices = [
        Index(
            value = ["campaignId", "status"],
            name = "index_broadcast_logs_campaignId_status"
        ),
        Index(
            value = ["timestamp"],
            name = "index_broadcast_logs_timestamp"
        )
    ]
)
data class BroadcastLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val contactId: Long,
    val contactName: String,
    val phoneNumber: String,
    val resolvedMessage: String,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val isGroup: Boolean = false
)

@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "General",
    val content: String,
    val usageCount: Int = 0
)

data class CampaignDashboardAggregate(
    val totalCampaigns: Int = 0,
    val totalSent: Int = 0,
    val totalDelivered: Int = 0,
    val totalRead: Int = 0,
    val totalFailed: Int = 0,
    val scheduledCount: Int = 0,
    val queuedCount: Int = 0,
    val runningCount: Int = 0
)

data class WhatsAppInstance(
    val packageName: String,
    val label: String,
    val userSerial: Long = 0L,
    val isBusiness: Boolean = false,
    val isClone: Boolean = false
) {
    val stableId: String get() = "$packageName#$userSerial"
}

data class DailyPacingEstimate(
    val totalSameDayRecipients: Int = 0,
    val estimatedTotalSeconds: Long = 0L,
    val overlappingCampaignTitles: List<String> = emptyList(),
    val isHeavyLoad: Boolean = false,
    val isOverCapacity: Boolean = false
)
