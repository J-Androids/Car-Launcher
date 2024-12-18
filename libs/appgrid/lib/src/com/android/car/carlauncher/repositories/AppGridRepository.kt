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

package com.android.car.carlauncher.repositories

import android.Manifest.permission.MANAGE_OWN_CALLS
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.util.Log
import com.android.car.carlauncher.AppItem
import com.android.car.carlauncher.AppMetaData
import com.android.car.carlauncher.datasources.AppOrderDataSource
import com.android.car.carlauncher.datasources.AppOrderDataSource.AppOrderInfo
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSource
import com.android.car.carlauncher.datasources.LauncherActivitiesDataSource
import com.android.car.carlauncher.datasources.MediaTemplateAppsDataSource
import com.android.car.carlauncher.datasources.UXRestrictionDataSource
import com.android.car.carlauncher.datasources.restricted.DisabledAppsDataSource
import com.android.car.carlauncher.datasources.restricted.TosDataSource
import com.android.car.carlauncher.datasources.restricted.TosState
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory.AppLauncherProviderType
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory.AppLauncherProviderType.DISABLED
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory.AppLauncherProviderType.LAUNCHER
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory.AppLauncherProviderType.MEDIA
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory.AppLauncherProviderType.MIRRORING
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory.AppLauncherProviderType.TOS_DISABLED
import com.android.car.carlauncher.repositories.appactions.AppShortcutsFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface AppGridRepository {

    /**
     * Returns a flow of all applications available in the app grid, including
     * system apps, media apps, and potentially restricted apps.
     *
     * @return A Flow emitting a list of AppItem objects.
     */
    fun getAllAppsList(): Flow<List<AppItem>>

    /**
     * Provides a flow indicating whether distraction optimization is required for the device.
     * Distraction optimization might limit the features or visibility of apps.
     *
     * @return A Flow emitting a Boolean value, where `true` indicates distraction optimization is
     * needed.
     */
    fun requiresDistractionOptimization(): Flow<Boolean>

    /**
     * Returns a continuous flow representing the Terms of Service (ToS) state for apps.
     * This state may determine the availability or restrictions of certain apps.
     *
     * @return A Flow emitting the current TosState.
     */
    fun getTosState(): Flow<TosState>

    /**
     * Suspends execution to save the provided app order to persistent storage.
     * Used to maintain the arrangement of apps within the app grid.
     *
     * @param currentAppOrder A list of AppItem representing the desired app order.
     */
    suspend fun saveAppOrder(currentAppOrder: List<AppItem>)

    /**
     * Returns a flow of media-related apps installed on the device.
     *
     * @return A Flow emitting a list of AppItem representing media applications.
     */
    fun getMediaAppsList(): Flow<List<AppItem>>
}

/**
 * The core implementation of the AppGridRepository interface.  This class is responsible for:
 *
 * *  Fetching and combining app information from various sources (launcher activities,
 *    media services, disabled apps, etc.)
 * *  Applying restrictions and filtering app lists based on distraction optimization, ToS status,
 *    and mirroring state.
 * *  Managing app order and saving it to persistent storage.
 * *  Providing real-time updates of the app grid as changes occur.
 */
class AppGridRepositoryImpl(
    private val launcherActivities: LauncherActivitiesDataSource,
    private val mediaTemplateApps: MediaTemplateAppsDataSource,
    private val disabledApps: DisabledAppsDataSource,
    private val tosApps: TosDataSource,
    private val controlCenterMirroring: ControlCenterMirroringDataSource,
    private val uxRestriction: UXRestrictionDataSource,
    private val appOrder: AppOrderDataSource,
    private val packageManager: PackageManager,
    private val appLaunchFactory: AppLaunchProviderFactory,
    private val appShortcutsFactory: AppShortcutsFactory,
    userManager: UserManager,
    private val bgDispatcher: CoroutineDispatcher
) : AppGridRepository {

    private val isVisibleBackgroundUser = !userManager.isUserForeground &&
        userManager.isUserVisible && !userManager.isProfile

    /**
     * Provides a flow of all apps in the app grid.
     * It combines data from multiple sources, filters apps based on restrictions, handles dynamic
     * updates and returns the list in the last known savedOrder.
     *
     * @return A Flow emitting lists of AppItem objects.
     */
    override fun getAllAppsList(): Flow<List<AppItem>> {
        return combine(
            getAllLauncherAndMediaApps(),
            getRestrictedApps(),
            controlCenterMirroring.getAppMirroringSession(),
            appOrder.getSavedAppOrderComparator(),
            uxRestriction.isDistractionOptimized()
        ) { apps, restrictedApps, mirroringSession, order, isDistractionOptimized ->
            val alreadyAddedComponents = apps.map { it.componentName.packageName }.toSet()
            return@combine (apps + restrictedApps.filterNot {
                it.componentName.packageName in alreadyAddedComponents
            }).sortedWith { a1, a2 ->
                order.compare(a1.appOrderInfo, a2.appOrderInfo)
            }.filter {
                !shouldHideApp(it)
            }.map {
                if (mirroringSession.packageName == it.componentName.packageName) {
                    it.redirectIntent = mirroringSession.launchIntent
                } else if (it.launchActionType == MIRRORING) {
                    it.redirectIntent = null
                }
                it.toAppItem(isDistractionOptimized(it.componentName, it.launchActionType == MEDIA))
            }
        }.flowOn(bgDispatcher).distinctUntilChanged()
    }

    /**
     * Emitting distraction optimization status changes.
     *
     * @return A Flow of Boolean values, where `true` indicates distraction optimization is
     * required.
     */
    override fun requiresDistractionOptimization(): Flow<Boolean> {
        return uxRestriction.requiresDistractionOptimization()
    }

    /**
     * Provides the Terms of Service state for apps.
     *
     * @return A Flow emitting the current TosState.
     */
    override fun getTosState(): Flow<TosState> {
        return tosApps.getTosState()
    }

    /**
     *  Suspends saving the given app order to persistent storage.
     *  Updates to the app order are posted to the subscribers of
     *  [AppGridRepositoryImpl.getAllAppsList]
     *
     * @param currentAppOrder A list of AppItem representing the desired app order.
     */
    override suspend fun saveAppOrder(currentAppOrder: List<AppItem>) {
        appOrder.saveAppOrder(currentAppOrder.toAppOrderInfoList())
    }

    /**
     * Providing a flow of media-related apps.
     * Handles dynamic updates to the list of media apps.
     *
     * @return A Flow emitting lists of AppItem objects representing media apps.
     */
    override fun getMediaAppsList(): Flow<List<AppItem>> {
        return launcherActivities.getOnPackagesChanged().map {
            mediaTemplateApps.getAllMediaServices(true).map {
                it.toAppInfo(MEDIA).toAppItem(true)
            }
        }.flowOn(bgDispatcher).distinctUntilChanged()
    }

    private fun getAllLauncherAndMediaApps(): Flow<List<AppInfo>> {
        return launcherActivities.getOnPackagesChanged().map {
            val launcherApps = launcherActivities.getAllLauncherActivities().map {
                AppInfo(it.label, it.componentName, it.getBadgedIcon(0), LAUNCHER)
            }
            val mediaTemplateApps = mediaTemplateApps.getAllMediaServices(false).map {
                it.toAppInfo(MEDIA)
            }
            launcherApps + mediaTemplateApps
        }.flowOn(bgDispatcher).distinctUntilChanged()
    }

    private fun getRestrictedApps(): Flow<List<AppInfo>> {
        return disabledApps.getDisabledApps()
            .combine(tosApps.getTosState()) { disabledApps, tosApps ->
                return@combine disabledApps.map {
                    it.toAppInfo(DISABLED)
                } + tosApps.restrictedApps.map {
                    it.toAppInfo(TOS_DISABLED)
                }
            }.flowOn(bgDispatcher).distinctUntilChanged()
    }

    private data class AppInfo(
        val displayName: CharSequence,
        val componentName: ComponentName,
        val icon: Drawable,
        private val _launchActionType: AppLauncherProviderType,
        var redirectIntent: Intent? = null
    ) {
        val launchActionType get() = if (redirectIntent == null) {
            _launchActionType
        } else {
            MIRRORING
        }

        val appOrderInfo =
            AppOrderInfo(componentName.packageName, componentName.className, displayName.toString())
    }

    private fun AppInfo.toAppItem(isDistractionOptimized: Boolean): AppItem {
        val metaData = AppMetaData(
            displayName,
            componentName,
            icon,
            isDistractionOptimized,
            launchActionType == MIRRORING,
            launchActionType == TOS_DISABLED,
            { context ->
                appLaunchFactory
                    .get(launchActionType)
                    ?.launch(context, componentName, redirectIntent)
            },
            { contextViewPair ->
                appShortcutsFactory.showShortcuts(
                    componentName,
                    displayName,
                    contextViewPair.first,
                    contextViewPair.second
                )
            }
        )
        return AppItem(metaData)
    }

    private fun ResolveInfo.toAppInfo(launchActionType: AppLauncherProviderType): AppInfo {
        val componentName: ComponentName
        val icon: Drawable
        if (launchActionType == MEDIA) {
            componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
            icon = serviceInfo.loadIcon(packageManager)
        } else {
            componentName = ComponentName(activityInfo.packageName, activityInfo.name)
            icon = activityInfo.loadIcon(packageManager)
        }
        return AppInfo(
            loadLabel(packageManager),
            componentName,
            icon,
            launchActionType
        )
    }

    private fun List<AppItem>.toAppOrderInfoList(): List<AppOrderInfo> {
        return map { AppOrderInfo(it.packageName, it.className, it.displayName.toString()) }
    }

    private fun shouldHideApp(appInfo: AppInfo): Boolean {
        // Disable telephony apps for MUMD passenger since accepting a call will
        // drop the driver's call.
        if (isVisibleBackgroundUser) {
            return try {
                packageManager.getPackageInfo(
                    appInfo.componentName.packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions?.any {it == MANAGE_OWN_CALLS} ?: false
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Unable to query app permissions for $appInfo $e")
                false
            }
        }

        return false
    }

    companion object {
        const val TAG = "AppGridRepository"
    }
}
