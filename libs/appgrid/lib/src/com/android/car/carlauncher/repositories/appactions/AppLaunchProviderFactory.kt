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

package com.android.car.carlauncher.repositories.appactions

import android.car.media.CarMediaManager
import android.content.pm.PackageManager
import android.util.Log
import com.android.car.carlauncher.repositories.appactions.AppLaunchProvider.DisabledAppLaunchProvider
import com.android.car.carlauncher.repositories.appactions.AppLaunchProvider.LauncherActivityLaunchProvider
import com.android.car.carlauncher.repositories.appactions.AppLaunchProvider.MediaServiceLaunchProvider
import com.android.car.carlauncher.repositories.appactions.AppLaunchProvider.MirroringAppLaunchProvider
import com.android.car.carlauncher.repositories.appactions.AppLaunchProvider.TosDisabledAppLaunchProvider

/**
 * Acts as a central factory for obtaining specialized AppLaunchProvider instances.
 * It maintains an internal mapping between AppLauncherProviderType enum values and
 * the corresponding provider objects. The factory is responsible for:
 *
 * * Registering available AppLaunchProvider implementations
 * * Providing access to providers based on the requested AppLauncherProviderType
 */
class AppLaunchProviderFactory(
    carMediaManager: CarMediaManager,
    launchMediaCenter: Boolean,
    onMediaSelected: () -> Unit,
    packageManager: PackageManager
) {

    private val providerMap = mutableMapOf<AppLauncherProviderType, AppLaunchProvider>()

    /**
     * Enumerates the supported types of AppLaunchProvider implementations. Used
     * internally by the factory to differentiate and retrieve providers.
     */
    enum class AppLauncherProviderType {
        LAUNCHER, MEDIA, DISABLED, TOS_DISABLED, MIRRORING
    }

    init {
        // Add all providers.
        addProvider(LauncherActivityLaunchProvider)
        addProvider(MediaServiceLaunchProvider(carMediaManager, launchMediaCenter, onMediaSelected))
        addProvider(DisabledAppLaunchProvider(packageManager))
        addProvider(TosDisabledAppLaunchProvider)
        addProvider(MirroringAppLaunchProvider)
    }

    /**
     * Retrieves the AppLaunchProvider associated with the specified AppLauncherProviderType.
     *
     * @param providerType The type of AppLaunchProvider to retrieve.
     * @return The corresponding AppLaunchProvider if registered, otherwise null.
     */
    fun get(providerType: AppLauncherProviderType): AppLaunchProvider? {
        return providerMap[providerType].also {
            if (it == null) {
                Log.i(TAG, "Launch provider for ${providerType.name} missing")
            }
        }
    }

    /**
     * Registers an AppLaunchProvider within the factory. Providers are associated with
     * their corresponding AppLauncherProviderType.
     *
     * @param provider The AppLaunchProvider instance to register.
     */
    private fun addProvider(provider: AppLaunchProvider) {
        when (provider) {
            is DisabledAppLaunchProvider -> {
                providerMap[AppLauncherProviderType.DISABLED] = provider
            }

            LauncherActivityLaunchProvider -> {
                providerMap[AppLauncherProviderType.LAUNCHER] = provider
            }

            is MediaServiceLaunchProvider -> {
                providerMap[AppLauncherProviderType.MEDIA] = provider
            }

            is MirroringAppLaunchProvider -> {
                providerMap[AppLauncherProviderType.MIRRORING] = provider
            }

            is TosDisabledAppLaunchProvider -> {
                providerMap[AppLauncherProviderType.TOS_DISABLED] = provider
            }
        }
    }

    companion object {
        const val TAG = "AppLaunchProviderFactory"
    }
}
