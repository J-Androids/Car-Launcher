/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.carlauncher

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.savedstate.SavedStateRegistryOwner
import com.android.car.carlauncher.AppGridFragment.AppTypes.Companion.APP_TYPE_LAUNCHABLES
import com.android.car.carlauncher.AppGridFragment.Mode
import com.android.car.carlauncher.repositories.AppGridRepository
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * This ViewModel manages the main application grid within the car launcher. It provides
 * methods to retrieve app lists, handle app reordering, determine distraction
 * optimization requirements, and manage the Terms of Service (TOS) banner display.
 */
class AppGridViewModel(
    private val appGridRepository: AppGridRepository,
    private val application: Application
) : AndroidViewModel(application) {

    /**
     * A Kotlin Flow containing a complete list of applications obtained from the repository.
     * This Flow is shared for efficiency within the ViewModel.
     */
    private val allAppsItemList = appGridRepository.getAllAppsList()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIME_OUT_FLOW_SUBSCRIPTION), 1)

    /**
     * A Kotlin Flow containing a list of media-focused applications obtained from the repository,
     * shared for efficiency within the ViewModel.
     */
    private val mediaOnlyList = appGridRepository.getMediaAppsList()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIME_OUT_FLOW_SUBSCRIPTION), 1)

    /**
     * A MutableStateFlow indicating the current application display mode in the app grid.
     */
    private val appMode: MutableStateFlow<Mode> = MutableStateFlow(Mode.ALL_APPS)

    /**
     * Provides a Flow of application lists (AppItem). The returned Flow dynamically switches
     * between the complete app list (`allAppsItemList`) and a filtered list
     * of media apps (`mediaOnlyList`) based on the current `appMode`.
     *
     * @return A Flow of AppItem lists
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAppList(): Flow<List<AppItem>> {
        return appMode.transformLatest {
            val sourceList = if (it.appTypes and APP_TYPE_LAUNCHABLES == 1) {
                allAppsItemList
            } else {
                mediaOnlyList
            }
            emitAll(sourceList)
        }.distinctUntilChanged()
    }

    /**
     * Updates the application order in the repository.
     *
     * @param newPosition The intended new index position for the app.
     * @param appItem The AppItem to be repositioned.
     */
    fun saveAppOrder(newPosition: Int, appItem: AppItem) {
        viewModelScope.launch {
            allAppsItemList.replayCache.lastOrNull()?.toMutableList()?.apply {
                // Remove original occurrence
                remove(appItem)
                // Add to new position
                add(newPosition, appItem)
            }?.let {
                appGridRepository.saveAppOrder(it)
            }
        }
    }

    /**
     * Provides a flow indicating whether distraction optimization should be applied
     * in the car launcher UI.
     *
     * @return A Flow emitting Boolean values where 'true' signifies a need for distraction optimization.
     */
    fun requiresDistractionOptimization(): Flow<Boolean> {
        return appGridRepository.requiresDistractionOptimization()
    }

    /**
     * Returns a flow that determines whether the Terms of Service (TOS) banner should be displayed.
     * The logic considers if the TOS requires acceptance and the banner resurfacing interval.
     *
     * @return A Flow emitting Boolean values where 'true' indicates the banner should be displayed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getShouldShowTosBanner(): Flow<Boolean> {
        return appGridRepository.getTosState().mapLatest {
            if (!it.shouldBlockTosApps) {
                return@mapLatest false
            }
            return@mapLatest shouldShowTos()
        }
    }

    /**
     * Checks if we need to show the Banner based when it was previously dismissed.
     */
    private fun shouldShowTos(): Boolean {
        // Convert days to seconds
        val bannerResurfaceTimeInSeconds = TimeUnit.DAYS.toSeconds(
            application.resources
                .getInteger(R.integer.config_tos_banner_resurface_time_days).toLong()
        )
        val bannerDismissTime = PreferenceManager.getDefaultSharedPreferences(application)
            .getLong(TOS_BANNER_DISMISS_TIME_KEY, 0)

        val systemBootTime = Clock.systemUTC()
            .instant().epochSecond - TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime())
        // Show on next drive / reboot, when banner has not been dismissed in current session
        return if (bannerResurfaceTimeInSeconds == 0L) {
            // If banner is dismissed in current drive session, it will have a timestamp greater
            // than the system boot time timestamp.
            bannerDismissTime < systemBootTime
        } else {
            Clock.systemUTC()
            .instant().epochSecond - bannerDismissTime > bannerResurfaceTimeInSeconds
        }
    }

    /**
     * Saves the current timestamp to Preferences, marking the time when the Terms of Service (TOS)
     * banner was dismissed by the user.
     */
    fun saveTosBannerDismissalTime() {
        val dismissTime: Long = Clock.systemUTC().instant().epochSecond
        PreferenceManager.getDefaultSharedPreferences(application)
            .edit().putLong(TOS_BANNER_DISMISS_TIME_KEY, dismissTime).apply()
    }

    /**
     * Updates the current application display mode. This triggers UI updates in the app grid.
     * @param mode The new Mode to set for the application grid.
     */
    fun updateMode(mode: Mode) {
        appMode.value = mode
    }

    companion object {
        const val TOS_BANNER_DISMISS_TIME_KEY = "TOS_BANNER_DISMISS_TIME"
        const val STOP_TIME_OUT_FLOW_SUBSCRIPTION = 5_000L
        fun provideFactory(
            myRepository: AppGridRepository,
            application: Application,
            owner: SavedStateRegistryOwner,
            defaultArgs: Bundle? = null,
        ): AbstractSavedStateViewModelFactory =
            object : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
                override fun <T : ViewModel> create(
                    key: String,
                    modelClass: Class<T>,
                    handle: SavedStateHandle
                ): T {
                    return AppGridViewModel(myRepository, application) as T
                }
            }
    }
}
