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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.service.media.MediaBrowserService
import com.android.car.media.common.source.MediaSource.isAudioMediaSource
import com.android.car.media.common.source.MediaSource.isMediaTemplate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * DataSource interface for MediaTemplate apps
 */
interface MediaTemplateAppsDataSource {

    /**
     * Get all media services on the device
     * @param includeCustomMediaPackages
     */
    suspend fun getAllMediaServices(includeCustomMediaPackages: Boolean): List<ResolveInfo>
}

/**
 * Impl of [MediaTemplateAppsDataSource], surfaces all media template apps related queries
 *
 * @property packageManager to query MediaServices.
 * @param appContext application context, not bound to activity's configuration changes.
 * @property [bgDispatcher] executes all the operations on this background coroutine dispatcher.
 */
class MediaTemplateAppsDataSourceImpl(
    private val packageManager: PackageManager,
    private val appContext: Context,
    private val bgDispatcher: CoroutineDispatcher
) : MediaTemplateAppsDataSource {

    /**
     * Gets all media services for MediaTemplateApps.
     *
     * @param includeCustomMediaPackages if false, only gets MediaTemplateApps.
     *                                   if true, in addition to MediaTemplateApps also include
     *                                   custom media components.
     */
    override suspend fun getAllMediaServices(
        includeCustomMediaPackages: Boolean
    ): List<ResolveInfo> {
        val filterFunction = if (includeCustomMediaPackages) {
            ::isAudioMediaSource
        } else {
            ::isMediaTemplate
        }
        return withContext(bgDispatcher) {
            packageManager.queryIntentServices(
                Intent(MediaBrowserService.SERVICE_INTERFACE),
                PackageManager.GET_RESOLVED_FILTER
            ).filter {
                val componentName = ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
                filterFunction(appContext, componentName)
            }
        }
    }

    companion object {
        val TAG: String = MediaTemplateAppsDataSourceImpl::class.java.simpleName
    }
}
