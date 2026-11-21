package com.example.util

import com.example.data.model.AutomationStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

data class BroadcastExecutionState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val activeCampaignId: Long? = null,
    val activeCampaignTitle: String = "",
    val currentRecipientIndex: Int = 0,
    val totalRecipients: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val currentRecipientName: String = "",
    val currentRecipientPhone: String = "",
    val currentStep: AutomationStep = AutomationStep.IDLE,
    val cooldownSecondsRemaining: Int = 0,
    val queuedCampaignIds: List<Long> = emptyList(),
    val lastStatusNote: String = "",
    val startedWhileLocked: Boolean = false
)

/**
 * Process-wide single source of truth preventing concurrent WhatsApp window collisions
 * and coordinating persistent FIFO queueing, Pause/Resume/Skip/Stop, and incoming call interrupts.
 */
object BroadcastExecutionCoordinator {

    private val activeExecutionFlag = AtomicBoolean(false)
    val executionMutex = Mutex()

    private val _state = MutableStateFlow(BroadcastExecutionState())
    val state: StateFlow<BroadcastExecutionState> = _state.asStateFlow()

    @Volatile
    private var stopRequested = false

    @Volatile
    private var skipCurrentRequested = false

    val isActivelyExecuting: Boolean
        get() = activeExecutionFlag.get()

    val isStopRequested: Boolean
        get() = stopRequested

    fun tryBeginExecution(
        campaignId: Long,
        title: String,
        totalRecipients: Int,
        startedWhileLocked: Boolean = false
    ): Boolean {
        if (!activeExecutionFlag.compareAndSet(false, true)) {
            return false
        }
        stopRequested = false
        skipCurrentRequested = false
        _state.update { current ->
            current.copy(
                isRunning = true,
                isPaused = false,
                pauseReason = null,
                activeCampaignId = campaignId,
                activeCampaignTitle = title,
                currentRecipientIndex = 0,
                totalRecipients = totalRecipients,
                sentCount = 0,
                failedCount = 0,
                currentStep = if (startedWhileLocked) AutomationStep.SWIPING_LOCKSCREEN else AutomationStep.AWAITING_DISPATCH,
                cooldownSecondsRemaining = 0,
                queuedCampaignIds = current.queuedCampaignIds.filterNot { it == campaignId },
                lastStatusNote = if (startedWhileLocked) "Waking & unlocking device..." else "Starting zero-tap broadcast...",
                startedWhileLocked = startedWhileLocked
            )
        }
        return true
    }

    fun updateStep(step: AutomationStep, note: String = step.label) {
        _state.update { current ->
            current.copy(currentStep = step, lastStatusNote = note)
        }
    }

    /**
     * Pure decision rule: only auto-lock the device after completion if the broadcast
     * started while locked, the setting is enabled, and the user did not stop or answer a call.
     */
    fun shouldAutoLockAfterCompletion(
        startedWhileLocked: Boolean,
        autoLockEnabled: Boolean,
        stoppedByUser: Boolean,
        pausedForCall: Boolean
    ): Boolean {
        return startedWhileLocked && autoLockEnabled && !stoppedByUser && !pausedForCall
    }

    fun isCampaignActiveOrQueued(campaignId: Long): Boolean {
        val current = _state.value
        return (current.isRunning && current.activeCampaignId == campaignId) ||
            campaignId in current.queuedCampaignIds
    }

    fun syncInitialCounts(sentCount: Int, failedCount: Int) {
        _state.update { current ->
            current.copy(
                sentCount = sentCount,
                failedCount = failedCount,
                currentRecipientIndex = (sentCount + failedCount).coerceAtLeast(current.currentRecipientIndex)
            )
        }
    }

    fun switchToNextQueuedCampaign(campaignId: Long, title: String, totalRecipients: Int) {
        stopRequested = false
        skipCurrentRequested = false
        _state.update { current ->
            current.copy(
                isRunning = true,
                isPaused = false,
                pauseReason = null,
                activeCampaignId = campaignId,
                activeCampaignTitle = title,
                currentRecipientIndex = 0,
                totalRecipients = totalRecipients,
                sentCount = 0,
                failedCount = 0,
                currentStep = AutomationStep.AWAITING_DISPATCH,
                cooldownSecondsRemaining = 0,
                queuedCampaignIds = current.queuedCampaignIds.filterNot { it == campaignId },
                lastStatusNote = "Advancing to queued campaign..."
            )
        }
    }

    fun enqueueCampaign(campaignId: Long) {
        _state.update { current ->
            if ((current.isRunning && current.activeCampaignId == campaignId) ||
                campaignId in current.queuedCampaignIds
            ) {
                current
            } else {
                current.copy(queuedCampaignIds = current.queuedCampaignIds + campaignId)
            }
        }
    }

    fun removeFromQueue(campaignId: Long) {
        _state.update { current ->
            current.copy(queuedCampaignIds = current.queuedCampaignIds.filterNot { it == campaignId })
        }
    }

    fun popNextQueuedCampaign(): Long? {
        var nextId: Long? = null
        _state.update { current ->
            nextId = current.queuedCampaignIds.firstOrNull()
            if (nextId != null) {
                current.copy(queuedCampaignIds = current.queuedCampaignIds.drop(1))
            } else {
                current
            }
        }
        return nextId
    }

    fun updateProgress(
        recipientIndex: Int,
        recipientName: String,
        recipientPhone: String,
        sentCount: Int,
        failedCount: Int,
        step: AutomationStep,
        note: String = ""
    ) {
        _state.update { current ->
            current.copy(
                currentRecipientIndex = recipientIndex,
                currentRecipientName = recipientName,
                currentRecipientPhone = recipientPhone,
                sentCount = sentCount,
                failedCount = failedCount,
                currentStep = step,
                lastStatusNote = note.ifBlank { step.label }
            )
        }
    }

    fun updateCooldown(secondsRemaining: Int) {
        _state.update { current ->
            current.copy(
                cooldownSecondsRemaining = secondsRemaining.coerceAtLeast(0),
                currentStep = if (secondsRemaining > 0) AutomationStep.RESTING else current.currentStep
            )
        }
    }

    fun pause(reason: String = "Paused by user") {
        if (!activeExecutionFlag.get()) return
        _state.update { current ->
            current.copy(
                isPaused = true,
                pauseReason = reason,
                currentStep = AutomationStep.PAUSED,
                lastStatusNote = reason
            )
        }
    }

    fun resume() {
        if (!activeExecutionFlag.get()) return
        _state.update { current ->
            current.copy(
                isPaused = false,
                pauseReason = null,
                currentStep = AutomationStep.AWAITING_DISPATCH,
                lastStatusNote = "Resumed"
            )
        }
    }

    fun requestSkipCurrent() {
        if (activeExecutionFlag.get()) {
            skipCurrentRequested = true
        }
    }

    fun consumeSkipRequest(): Boolean {
        val requested = skipCurrentRequested
        skipCurrentRequested = false
        return requested
    }

    fun requestStop() {
        stopRequested = true
        _state.update { current ->
            current.copy(
                isPaused = false,
                lastStatusNote = "Stopping broadcast..."
            )
        }
    }

    fun finishExecution(clearQueue: Boolean = false) {
        activeExecutionFlag.set(false)
        stopRequested = false
        skipCurrentRequested = false
        _state.update { current ->
            BroadcastExecutionState(
                queuedCampaignIds = if (clearQueue) emptyList() else current.queuedCampaignIds
            )
        }
    }

    fun resetForTest() {
        finishExecution(clearQueue = true)
    }
}
