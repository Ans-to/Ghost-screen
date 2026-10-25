package com.ghostdisplay.app.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdisplay.app.GhostApp
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.launch.AppCatalog
import com.ghostdisplay.app.service.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GhostViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GhostApp
    private val catalog = AppCatalog(application.packageManager)

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val profiles = app.container.db.profiles().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions = SessionRepository.sessions

    val rows: StateFlow<List<PickerRow>> = combine(_apps, profiles) { apps, profs ->
        val map = profs.associateBy { it.packageName }
        val pm = getApplication<Application>().packageManager
        apps.map { a ->
            PickerRow(
                app = a,
                profile = map[a.packageName],
                icon = iconOf(pm, a.packageName),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val onboardingComplete = app.container.prefs.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val warningAccepted = app.container.prefs.warningAccepted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                catalog.listLaunchable(getApplication<Application>().packageName)
            }
            _apps.value = list
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun completeOnboarding() {
        viewModelScope.launch { app.container.prefs.setOnboardingComplete(true) }
    }

    fun acceptWarning() {
        viewModelScope.launch { app.container.prefs.setWarningAccepted(true) }
    }

    fun saveProfile(profile: AppProfile) {
        viewModelScope.launch { app.container.db.profiles().upsert(profile) }
    }

    suspend fun profileOf(packageName: String): AppProfile {
        return app.container.db.profiles().get(packageName) ?: AppProfile(packageName)
    }

    fun findApp(packageName: String): LaunchableApp? =
        _apps.value.firstOrNull { it.packageName == packageName }

    private fun iconOf(pm: PackageManager, pkg: String) = try {
        pm.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }
}
