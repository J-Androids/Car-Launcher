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

package com.android.car.carlauncher.datasources

import android.car.content.pm.CarPackageManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.ComponentName
import android.content.res.Resources
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.util.Log
import com.android.car.carlauncher.Flags
import com.android.car.carlauncher.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * DataSource interface for providing ux restriction state
 */
interface UXRestrictionDataSource {

    /**
     * Flow notifying if distraction optimization is required
     */
    fun requiresDistractionOptimization(): Flow<Boolean>

    fun isDistractionOptimized(): Flow<(componentName: ComponentName, isMedia: Boolean) -> Boolean>
}

/**
 * Impl of [UXRestrictionDataSource]
 *
 * @property [uxRestrictionsManager] Used to listen for distraction optimization changes.
 * @property [carPackageManager]
 * @property [mediaSessionManager]
 * @property [resources] Application resources, not bound to activity's configuration changes.
 * @property [bgDispatcher] Executes all the operations on this background coroutine dispatcher.
 */
class UXRestrictionDataSourceImpl(
    private val uxRestrictionsManager: CarUxRestrictionsManager,
    private val carPackageManager: CarPackageManager,
    private val mediaSessionManager: MediaSessionManager,
    private val resources: Resources,
    private val bgDispatcher: CoroutineDispatcher = Dispatchers.Default
) : UXRestrictionDataSource {

    /**
     * Gets a flow producer which provides updates if distraction optimization is currently required
     * This conveys if the foreground activity needs to be distraction optimized.
     *
     * When the scope in which this flow is collected is closed/canceled
     * [CarUxRestrictionsManager.unregisterListener] is triggered.
     */
    override fun requiresDistractionOptimization(): Flow<Boolean> {
        return callbackFlow {
            val currentRestrictions = uxRestrictionsManager.currentCarUxRestrictions
            if (currentRestrictions == null) {
                Log.e(TAG, "CurrentCarUXRestrictions is not initialized")
                trySend(false)
            } else {
                trySend(currentRestrictions.isRequiresDistractionOptimization)
            }
            uxRestrictionsManager.registerListener {
                trySend(it.isRequiresDistractionOptimization)
            }
            awaitClose {
                uxRestrictionsManager.unregisterListener()
            }
        }.flowOn(bgDispatcher).conflate()
    }

    override fun isDistractionOptimized():
            Flow<(componentName: ComponentName, isMedia: Boolean) -> Boolean> {
        if (!(Flags.mediaSessionCard() &&
                    resources.getBoolean(R.bool.config_enableMediaSessionAppsWhileDriving))
        ) {
            return flowOf(fun(componentName: ComponentName, isMedia: Boolean): Boolean {
                return isMedia || (carPackageManager.isActivityDistractionOptimized(
                    componentName.packageName,
                    componentName.className
                ))
            })
        }
        return getActiveMediaPlaybackSessions().map {
            fun(componentName: ComponentName, isMedia: Boolean): Boolean {
                if (it.contains(componentName.packageName)) {
                    return true
                }
                return isMedia || (carPackageManager.isActivityDistractionOptimized(
                    componentName.packageName,
                    componentName.className
                ))
            }
        }.distinctUntilChanged()
    }

    private fun getActiveMediaPlaybackSessions(): Flow<List<String>> {
        return callbackFlow {
            val filterActiveMediaPackages: (List<MediaController>) -> List<String> =
                { mediaControllers ->
                    mediaControllers.filter {
                        it.playbackState?.isActive ?: false
                    }.map { it.packageName }
                }
            // Emits the initial list of filtered packages upon subscription
            trySend(
                filterActiveMediaPackages(mediaSessionManager.getActiveSessions(null))
            )
            val sessionsChangedListener =
                OnActiveSessionsChangedListener {
                    if (it != null) {
                        trySend(filterActiveMediaPackages(it))
                    }
                }
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, null)
            awaitClose {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            }
            // Note this flow runs on the Main dispatcher, as the MediaSessionsChangedListener
            // expects to dispatch updates on the Main looper.
        }.flowOn(Dispatchers.Main).conflate()
    }

    companion object {
        val TAG: String = UXRestrictionDataSourceImpl::class.java.simpleName
    }
}
