package com.safecallai.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.ReportCategory
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.service.CallScreeningRoleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isRoleHeld: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val themeMode: com.safecallai.app.data.local.PreferencesManager.ThemeMode = com.safecallai.app.data.local.PreferencesManager.ThemeMode.SYSTEM_DEFAULT,
    val appLanguage: com.safecallai.app.data.local.AppLanguage = com.safecallai.app.data.local.AppLanguage.ENGLISH,
    val totalScreenedCount: Int = 0,
    val falseSpamRecoveredCount: Int = 0,
    val verifiedCount: Int = 0,
    val isExpectingDelivery: Boolean = false,
    val isProtectionEnabled: Boolean = true,
    val isAggressiveBlockingEnabled: Boolean = false,
    val isOfflineModeForced: Boolean = false,
    val recentCalls: List<ScreenedCallRecord> = emptyList(),
    val trustedCallers: List<UserTrustedCaller> = emptyList(),
    val searchedResult: ClassificationResult? = null,
    val isSearching: Boolean = false,
    val isSubmittingReport: Boolean = false,
    val reportSuccessMessage: String? = null,
    val reportErrorMessage: String? = null
)

class SafeCallViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SafeCallApplication
    private val repository = app.repository
    private val roleManager = CallScreeningRoleManager(application)
    private val defaultDialerManager = com.safecallai.app.service.DefaultDialerManager(application)

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            themeMode = repository.preferences.themeMode,
            appLanguage = repository.preferences.appLanguage
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            val roleHeld = roleManager.isCallScreeningRoleHeld()
            val isDefaultDialer = defaultDialerManager.isDefaultDialer()
            val stats = repository.getDashboardStats()
            val recent = repository.getRecentCallHistory(30)
            val trusted = repository.getTrustedCallers()

            _uiState.value = _uiState.value.copy(
                isRoleHeld = roleHeld,
                isDefaultDialer = isDefaultDialer,
                themeMode = repository.preferences.themeMode,
                appLanguage = repository.preferences.appLanguage,
                totalScreenedCount = stats.first,
                falseSpamRecoveredCount = stats.second,
                verifiedCount = stats.third,
                isExpectingDelivery = repository.preferences.isUserExpectingDelivery,
                isProtectionEnabled = repository.preferences.isProtectionEnabled,
                isAggressiveBlockingEnabled = repository.preferences.isAggressiveBlockingEnabled,
                isOfflineModeForced = repository.preferences.isOfflineModeForced,
                recentCalls = recent,
                trustedCallers = trusted
            )
        }
    }

    fun getDefaultDialerManager(): com.safecallai.app.service.DefaultDialerManager = defaultDialerManager

    fun simulateCustomCall(context: android.content.Context, rawNumber: String, contactName: String? = null) {
        com.safecallai.app.service.CallManager.simulateIncomingCall(context, rawNumber, contactName) {
            refreshState()
        }
    }

    fun setThemeMode(mode: com.safecallai.app.data.local.PreferencesManager.ThemeMode) {
        repository.preferences.themeMode = mode
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setLanguage(language: com.safecallai.app.data.local.AppLanguage, context: android.content.Context) {
        repository.preferences.appLanguage = language
        repository.preferences.hasSelectedLanguage = true
        com.safecallai.app.util.LocaleHelper.applyLanguage(context, language)
        _uiState.value = _uiState.value.copy(appLanguage = language)
        refreshState()
    }

    fun evaluateNumber(rawNumber: String) {
        if (rawNumber.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val result = repository.screenAndClassify(rawNumber, recordInHistory = false)
            _uiState.value = _uiState.value.copy(
                searchedResult = result,
                isSearching = false
            )
        }
    }

    fun clearSearchedResult() {
        _uiState.value = _uiState.value.copy(searchedResult = null)
    }

    fun addTrustedCaller(phoneNumber: String, customLabel: String, notes: String? = null) {
        viewModelScope.launch {
            repository.addTrustedCaller(phoneNumber, customLabel, notes)
            refreshState()
        }
    }

    fun removeTrustedCaller(id: Long) {
        viewModelScope.launch {
            repository.removeTrustedCaller(id)
            refreshState()
        }
    }

    fun toggleUserExpectingDelivery(expecting: Boolean) {
        repository.preferences.isUserExpectingDelivery = expecting
        _uiState.value = _uiState.value.copy(isExpectingDelivery = expecting)
    }

    fun toggleProtection(enabled: Boolean) {
        repository.preferences.isProtectionEnabled = enabled
        _uiState.value = _uiState.value.copy(isProtectionEnabled = enabled)
    }

    fun toggleAggressiveBlocking(enabled: Boolean) {
        repository.preferences.isAggressiveBlockingEnabled = enabled
        _uiState.value = _uiState.value.copy(isAggressiveBlockingEnabled = enabled)
    }

    fun toggleOfflineMode(forced: Boolean) {
        repository.preferences.isOfflineModeForced = forced
        _uiState.value = _uiState.value.copy(isOfflineModeForced = forced)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            refreshState()
        }
    }

    fun submitReport(phoneNumber: String, category: ReportCategory, description: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmittingReport = true,
                reportSuccessMessage = null,
                reportErrorMessage = null
            )

            val result = repository.submitReport(phoneNumber, category, description)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingReport = false,
                        reportSuccessMessage = "Report saved locally on your device. Thank you for helping refine false spam detection."
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSubmittingReport = false,
                        reportErrorMessage = error.message ?: "Failed to save report locally."
                    )
                }
            )
        }
    }

    fun clearReportMessages() {
        _uiState.value = _uiState.value.copy(
            reportSuccessMessage = null,
            reportErrorMessage = null
        )
    }

    fun purgeAllLocalData() {
        viewModelScope.launch {
            repository.clearAllLocalData()
            refreshState()
        }
    }

    fun completeOnboarding() {
        repository.preferences.hasCompletedOnboarding = true
    }

    fun getRepository() = repository
    fun getRoleManager() = roleManager
}
