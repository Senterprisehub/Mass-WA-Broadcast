package com.example.util

import com.example.data.model.AccessibilityScopeMode
import com.example.data.model.AutomationStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

data class ActiveDispatchTask(
    val generation: Long,
    val recipientName: String,
    val recipientPhone: String,
    val previousRecipientName: String?,
    val previousRecipientPhone: String?,
    val message: String,
    val isGroup: Boolean,
    val hasMedia: Boolean,
    val targetPackage: String,
    val isCloneTarget: Boolean,
    val resultDeferred: CompletableDeferred<AutomationDispatchOutcome>
)

data class AutomationDispatchOutcome(
    val success: Boolean,
    val errorMessage: String? = null,
    val needsTextFollowUp: Boolean = false
)

/**
 * Coordinates the Accessibility automation state machine, banking-safe dynamic package scoping,
 * two-phase OEM device unlock, post-broadcast WhatsApp exit & conditional screen lock,
 * active watchdog signaling, and atomic generation tokens so delayed callbacks never cross-contaminate.
 */
object WhatsAppAutomationController {

    private val generationCounter = AtomicLong(0L)

    private val _scopeMode = MutableStateFlow(AccessibilityScopeMode.DISABLED)
    val scopeMode: StateFlow<AccessibilityScopeMode> = _scopeMode.asStateFlow()

    private val _currentStep = MutableStateFlow(AutomationStep.IDLE)
    val currentStep: StateFlow<AutomationStep> = _currentStep.asStateFlow()

    @Volatile
    var isServiceConnected: Boolean = false
        private set

    @Volatile
    var activeTask: ActiveDispatchTask? = null
        private set

    @Volatile
    var scopeUpdateListener: ((AccessibilityScopeMode) -> Unit)? = null

    @Volatile
    var taskStartedListener: ((ActiveDispatchTask) -> Unit)? = null

    @Volatile
    var disableSelfListener: (() -> Unit)? = null

    @Volatile
    var twoPhaseUnlockListener: ((CompletableDeferred<Boolean>) -> Unit)? = null

    @Volatile
    var exitAndLockListener: ((Boolean, Boolean, CompletableDeferred<Boolean>) -> Unit)? = null

    @Volatile
    var navigateHomeForInstanceSwitchListener: ((CompletableDeferred<Boolean>) -> Unit)? = null

    fun onServiceConnected() {
        isServiceConnected = true
        recomputeScope(needsLockscreenUnlock = false)
    }

    fun onServiceDisconnected() {
        isServiceConnected = false
        _scopeMode.value = AccessibilityScopeMode.DISABLED
        _currentStep.value = AutomationStep.IDLE
        activeTask?.resultDeferred?.complete(
            AutomationDispatchOutcome(false, "Accessibility service disconnected")
        )
        activeTask = null
    }

    fun onPickerSessionChanged(active: Boolean) {
        if (!isServiceConnected) return
        recomputeScope(needsLockscreenUnlock = false, pickerActive = active)
    }

    fun requestLockscreenUnlockScope() {
        if (!isServiceConnected) return
        _currentStep.value = AutomationStep.SWIPING_LOCKSCREEN
        setScope(AccessibilityScopeMode.SCOPED_LOCK_UNLOCK)
    }

    fun onKeyguardCleared() {
        if (!isServiceConnected) return
        recomputeScope(needsLockscreenUnlock = false)
    }

    /**
     * Executes two-phase OEM lockscreen unlock (Swipe Up -> Bouncer PIN/Pattern/Password)
     * strictly inside SCOPED_LOCK_UNLOCK and immediately drops systemui scope afterward.
     */
    suspend fun performTwoPhaseDeviceUnlock(timeoutMs: Long = 12_000L): Boolean {
        if (!isServiceConnected) return false
        val listener = twoPhaseUnlockListener ?: return false
        requestLockscreenUnlockScope()
        val deferred = CompletableDeferred<Boolean>()
        listener.invoke(deferred)
        val unlocked = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        onKeyguardCleared()
        return unlocked
    }

    /**
     * Exits WhatsApp back to Home so WhatsApp App Lock re-engages, and optionally locks
     * the device immediately via GLOBAL_ACTION_LOCK_SCREEN (Android 9+).
     */
    suspend fun performExitWhatsAppAndConditionalLock(
        exitToHome: Boolean,
        lockDevice: Boolean,
        timeoutMs: Long = 5_000L
    ): Boolean {
        if (!isServiceConnected) return false
        val listener = exitAndLockListener ?: return false
        if (exitToHome) {
            _currentStep.value = AutomationStep.EXITING_WHATSAPP
        } else if (lockDevice) {
            _currentStep.value = AutomationStep.LOCKING_DEVICE
        }
        val deferred = CompletableDeferred<Boolean>()
        listener.invoke(exitToHome, lockDevice, deferred)
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
    }

    /**
     * Performs a brief HOME navigation settle when consecutive FIFO campaigns switch
     * between different WhatsApp instances (e.g., WhatsApp Primary -> WhatsApp Business 2nd Clone)
     * so MIUI / ColorOS / Funtouch release the prior WhatsApp task stack cleanly.
     */
    suspend fun performHomeSettleForInstanceSwitch(timeoutMs: Long = 2_000L): Boolean {
        if (!isServiceConnected) return false
        val listener = navigateHomeForInstanceSwitchListener ?: return false
        val deferred = CompletableDeferred<Boolean>()
        listener.invoke(deferred)
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
    }

    /**
     * MUST be called BEFORE firing WhatsAppSender.dispatchMessage(...) so SCOPED_WHATSAPP
     * and the active screen watchdog are armed before the system chooser or WhatsApp opens.
     */
    fun beginRecipientTask(
        recipientName: String,
        recipientPhone: String,
        previousRecipientName: String?,
        previousRecipientPhone: String?,
        message: String,
        isGroup: Boolean,
        hasMedia: Boolean,
        targetPackage: String,
        isCloneTarget: Boolean
    ): ActiveDispatchTask {
        val gen = generationCounter.incrementAndGet()
        val task = ActiveDispatchTask(
            generation = gen,
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            previousRecipientName = previousRecipientName,
            previousRecipientPhone = previousRecipientPhone,
            message = message,
            isGroup = isGroup,
            hasMedia = hasMedia,
            targetPackage = targetPackage,
            isCloneTarget = isCloneTarget,
            resultDeferred = CompletableDeferred()
        )
        activeTask = task
        _currentStep.value = if (isGroup) AutomationStep.SEARCHING_RECIPIENT else AutomationStep.OPENING_CHAT
        if (isServiceConnected) {
            setScope(AccessibilityScopeMode.SCOPED_WHATSAPP)
            taskStartedListener?.invoke(task)
        }
        return task
    }

    suspend fun awaitRecipientOutcome(task: ActiveDispatchTask): AutomationDispatchOutcome {
        val timeoutMs = when {
            task.isGroup && task.hasMedia -> 30_000L
            task.isGroup || task.hasMedia || task.isCloneTarget -> 26_000L
            else -> 20_000L
        }
        val result = withTimeoutOrNull(timeoutMs) {
            task.resultDeferred.await()
        }
        if (activeTask?.generation == task.generation) {
            activeTask = null
        }
        return result ?: AutomationDispatchOutcome(
            success = false,
            errorMessage = "Timed out waiting for WhatsApp auto-send (${timeoutMs / 1000}s)"
        )
    }

    fun completeActiveTask(generation: Long, outcome: AutomationDispatchOutcome) {
        val task = activeTask ?: return
        if (task.generation != generation) return
        _currentStep.value = if (outcome.success) AutomationStep.RETURNING_TO_MAIN else AutomationStep.IDLE
        task.resultDeferred.complete(outcome)
        activeTask = null
    }

    fun endBroadcastSession(autoDisableAccessibility: Boolean) {
        activeTask?.resultDeferred?.complete(AutomationDispatchOutcome(false, "Session ended"))
        activeTask = null
        _currentStep.value = AutomationStep.IDLE
        if (autoDisableAccessibility && isServiceConnected) {
            disableSelfListener?.invoke()
            setScope(AccessibilityScopeMode.DISABLED)
        } else if (isServiceConnected) {
            recomputeScope(needsLockscreenUnlock = false)
        }
    }

    fun requestDisableServiceNow() {
        disableSelfListener?.invoke()
        setScope(AccessibilityScopeMode.DISABLED)
    }

    fun resolveAllowedPackages(
        mode: AccessibilityScopeMode,
        ownPackageName: String
    ): Array<String> {
        val dispatchPackages = arrayOf(
            ownPackageName,
            WhatsAppInstanceDiscovery.PKG_WHATSAPP,
            WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS,
            "android",
            "com.android.intentresolver",
            "com.vivo.doubleinstance",
            "com.vivo.appclone",
            "com.vivo.daemonService",
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
        return when (mode) {
            AccessibilityScopeMode.DISABLED -> emptyArray()
            AccessibilityScopeMode.IDLE_ISOLATED -> arrayOf(ownPackageName)
            AccessibilityScopeMode.SCOPED_WHATSAPP -> dispatchPackages
            AccessibilityScopeMode.SCOPED_LOCK_UNLOCK -> dispatchPackages + "com.android.systemui"
        }
    }

    private fun recomputeScope(
        needsLockscreenUnlock: Boolean,
        pickerActive: Boolean = WhatsAppPickerCoordinator.state.value.isActive
    ) {
        val next = when {
            !isServiceConnected -> AccessibilityScopeMode.DISABLED
            needsLockscreenUnlock -> AccessibilityScopeMode.SCOPED_LOCK_UNLOCK
            activeTask != null || BroadcastExecutionCoordinator.isActivelyExecuting || pickerActive -> {
                AccessibilityScopeMode.SCOPED_WHATSAPP
            }
            else -> AccessibilityScopeMode.IDLE_ISOLATED
        }
        setScope(next)
    }

    private fun setScope(mode: AccessibilityScopeMode) {
        _scopeMode.value = mode
        scopeUpdateListener?.invoke(mode)
    }
}
