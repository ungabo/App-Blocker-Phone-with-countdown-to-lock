package com.gabe.focusblocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gabe.focusblocker.FocusBlockerApplication
import com.gabe.focusblocker.SessionNotificationHelper
import com.gabe.focusblocker.data.entity.RuleMode
import com.gabe.focusblocker.data.entity.SessionSource
import com.gabe.focusblocker.engine.PinCheckResult
import com.gabe.focusblocker.repository.AppSettings
import com.gabe.focusblocker.repository.InstalledAppInfo
import com.gabe.focusblocker.repository.RuleSetDetail
import com.gabe.focusblocker.repository.ScheduledLockRepository
import com.gabe.focusblocker.util.DomainUtils
import com.gabe.focusblocker.util.TimeUtils
import com.gabe.focusblocker.widget.FocusWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RuleSetCardUi(
    val id: Long,
    val name: String,
    val mode: RuleMode,
    val defaultDurationLabel: String,
    val appCount: Int,
    val domainCount: Int,
    val isSystemPreset: Boolean
)

data class ActiveSessionUi(
    val id: Long,
    val ruleSetId: Long,
    val ruleSetName: String,
    val mode: RuleMode,
    val remainingLabel: String,
    val remainingMs: Long?,
    val sourceLabel: String
)

data class ScheduledLockUi(
    val id: Long,
    val ruleSetId: Long,
    val ruleSetName: String,
    val startsInLabel: String,
    val startsAt: Long,
    val durationMinutes: Int,
    val enabled: Boolean,
    val sourceLabel: String
)

data class RuleSetEditorDraft(
    val id: Long? = null,
    val isSystemPreset: Boolean = false,
    val name: String = "",
    val mode: RuleMode = RuleMode.BLOCK_LIST,
    val defaultDurationMinutesText: String = "",
    val selectedPackages: Set<String> = emptySet(),
    val domains: List<String> = emptyList()
)

class FocusBlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FocusBlockerApplication).container
    private val ruleSetRepository = container.ruleSetRepository
    private val sessionRepository = container.sessionRepository
    private val scheduledLockRepository = container.scheduledLockRepository
    private val installedAppsRepository = container.installedAppsRepository
    private val settingsRepository = container.settingsRepository
    private val pinManager = container.pinManager

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val ruleSets: StateFlow<List<RuleSetCardUi>> =
        ruleSetRepository.observeRuleSetSummaries()
            .map { summaries ->
                summaries.map {
                    RuleSetCardUi(
                        id = it.ruleSet.id,
                        name = it.ruleSet.name,
                        mode = it.ruleSet.mode,
                        defaultDurationLabel = TimeUtils.minutesToLabel(it.ruleSet.defaultDurationMinutes),
                        appCount = it.appCount,
                        domainCount = it.domainCount,
                        isSystemPreset = it.ruleSet.isSystemPreset
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSessions: StateFlow<List<ActiveSessionUi>> =
        sessionRepository.observeEnabledSessions()
            .combine(ticker) { sessions, now ->
                sessions
                    .filter { it.session.expiresAt == null || it.session.expiresAt > now }
                    .map {
                        ActiveSessionUi(
                            id = it.session.id,
                            ruleSetId = it.session.ruleSetId,
                            ruleSetName = it.ruleSetName,
                            mode = it.session.mode,
                            remainingLabel = TimeUtils.formatRemainingTime(
                                it.session.expiresAt?.minus(now)
                            ),
                            remainingMs = it.session.expiresAt?.minus(now),
                            sourceLabel = it.session.source.name
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val scheduledLocks: StateFlow<List<ScheduledLockUi>> =
        scheduledLockRepository.observeOpenLocks()
            .combine(ticker) { locks, now ->
                locks.map {
                    ScheduledLockUi(
                        id = it.scheduledLock.id,
                        ruleSetId = it.scheduledLock.ruleSetId,
                        ruleSetName = it.ruleSetName,
                        startsInLabel = TimeUtils.formatRemainingTime(it.scheduledLock.startsAt - now),
                        startsAt = it.scheduledLock.startsAt,
                        durationMinutes = it.scheduledLock.durationMinutes,
                        enabled = it.scheduledLock.enabled,
                        sourceLabel = it.scheduledLock.source.name
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _editorDraft = MutableStateFlow<RuleSetEditorDraft?>(null)
    val editorDraft = _editorDraft.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                sessionRepository.cleanupExpiredSessions()
                delay(30_000L)
            }
        }
    }

    fun loadInstalledApps(forceRefresh: Boolean = false) {
        if (_installedApps.value.isNotEmpty() && !forceRefresh) return
        viewModelScope.launch {
            _installedApps.value = installedAppsRepository.getLaunchableApps()
        }
    }

    fun prepareEditor(ruleSetId: Long?) {
        val currentDraft = _editorDraft.value
        if (currentDraft != null && currentDraft.id == ruleSetId) return

        viewModelScope.launch {
            _editorDraft.value = when (ruleSetId) {
                null -> RuleSetEditorDraft()
                else -> ruleSetRepository.observeRuleSetDetailOnce(ruleSetId)?.toDraft()
            } ?: RuleSetEditorDraft()
        }
    }

    fun clearEditor() {
        _editorDraft.value = null
    }

    fun updateDraftName(name: String) {
        _editorDraft.value = _editorDraft.value?.copy(name = name)
    }

    fun updateDraftMode(mode: RuleMode) {
        _editorDraft.value = _editorDraft.value?.copy(mode = mode)
    }

    fun updateDraftDefaultDuration(text: String) {
        _editorDraft.value = _editorDraft.value?.copy(defaultDurationMinutesText = text)
    }

    fun toggleDraftPackage(packageName: String) {
        val draft = _editorDraft.value ?: return
        val updated = draft.selectedPackages.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
        _editorDraft.value = draft.copy(selectedPackages = updated)
    }

    fun addDraftDomain(domain: String) {
        val normalized = DomainUtils.normalizeDomain(domain)
        if (normalized.isBlank()) return
        val draft = _editorDraft.value ?: return
        if (normalized in draft.domains) return
        _editorDraft.value = draft.copy(domains = draft.domains + normalized)
    }

    fun removeDraftDomain(domain: String) {
        val draft = _editorDraft.value ?: return
        _editorDraft.value = draft.copy(domains = draft.domains - domain)
    }

    fun saveDraft(onSaved: () -> Unit) {
        val draft = _editorDraft.value ?: return
        viewModelScope.launch {
            ruleSetRepository.saveRuleSet(
                existingId = draft.id,
                name = draft.name.ifBlank { "Untitled Rule Set" },
                mode = draft.mode,
                defaultDurationMinutes = draft.defaultDurationMinutesText.toIntOrNull(),
                selectedPackages = draft.selectedPackages,
                domains = draft.domains,
                isSystemPreset = draft.isSystemPreset
            )
            clearEditor()
            onSaved()
        }
    }

    fun startRuleSet(ruleSetId: Long, source: SessionSource = SessionSource.APP) {
        viewModelScope.launch {
            val ruleSet = ruleSetRepository.getRuleSetById(ruleSetId) ?: return@launch
            sessionRepository.startSession(ruleSet = ruleSet, source = source)
            refreshExternalStatus()
        }
    }

    fun scheduleLock(ruleSetId: Long, delayMinutes: Int, durationMinutes: Int, source: SessionSource = SessionSource.APP) {
        viewModelScope.launch {
            val ruleSet = ruleSetRepository.getRuleSetById(ruleSetId) ?: return@launch
            scheduledLockRepository.scheduleLock(
                ruleSet = ruleSet,
                delayMinutes = delayMinutes,
                durationMinutes = durationMinutes,
                source = source
            )
            refreshExternalStatus()
        }
    }

    fun duplicateRuleSet(ruleSetId: Long) {
        viewModelScope.launch {
            ruleSetRepository.duplicateRuleSet(ruleSetId)
        }
    }

    fun deleteRuleSet(ruleSetId: Long) {
        viewModelScope.launch {
            ruleSetRepository.deleteRuleSet(ruleSetId)
        }
    }

    fun endSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.endSession(sessionId)
            refreshExternalStatus()
        }
    }

    fun endAllSessions() {
        viewModelScope.launch {
            sessionRepository.endAllSessions()
            refreshExternalStatus()
        }
    }

    fun setScheduledLockEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            scheduledLockRepository.setEnabled(id, enabled)
            refreshExternalStatus()
        }
    }

    fun deleteScheduledLock(id: Long) {
        viewModelScope.launch {
            scheduledLockRepository.delete(id)
            refreshExternalStatus()
        }
    }

    fun clearScheduledLocks() {
        viewModelScope.launch {
            scheduledLockRepository.deleteOpenLocks()
            refreshExternalStatus()
        }
    }

    fun rescheduleLock(id: Long, delayMinutes: Int, durationMinutes: Int) {
        viewModelScope.launch {
            scheduledLockRepository.reschedule(id, delayMinutes, durationMinutes)
            refreshExternalStatus()
        }
    }

    fun setBlockSettingsApp(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBlockSettingsApp(enabled)
        }
    }

    fun setPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            pinManager.setPin(pin)
            onDone()
        }
    }

    fun clearPin(onDone: () -> Unit) {
        viewModelScope.launch {
            pinManager.clearPin()
            onDone()
        }
    }

    fun verifyPin(pin: String, onResult: (PinCheckResult) -> Unit) {
        viewModelScope.launch {
            onResult(pinManager.verify(pin))
        }
    }

    private suspend fun refreshExternalStatus() {
        SessionNotificationHelper.refresh(getApplication())
        FocusWidgetProvider.requestUpdate(getApplication())
    }

    private fun RuleSetDetail.toDraft(): RuleSetEditorDraft {
        return RuleSetEditorDraft(
            id = ruleSet.id,
            isSystemPreset = ruleSet.isSystemPreset,
            name = ruleSet.name,
            mode = ruleSet.mode,
            defaultDurationMinutesText = ruleSet.defaultDurationMinutes?.toString().orEmpty(),
            selectedPackages = selectedPackages,
            domains = domains
        )
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return FocusBlockerViewModel(application) as T
                }
            }
    }
}
