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
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import shadows.ShadowMediaSource

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMediaSource::class])
class MediaTemplateAppsDataSourceImplTest {

    private val scope = TestScope()
    private val appContext = RuntimeEnvironment.getApplication().applicationContext
    private val bgDispatcher =
        StandardTestDispatcher(scope.testScheduler, name = "Background dispatcher")

    private val listOfComponentNames = listOf(
        ComponentName("com.test.media.package1", "Media"), // 0, MediaService
        ComponentName("com.test.media.package2", "Media"), // 1, MediaService
        ComponentName(
            "com.test.custom.media.package2",
            "CustomMedia"
        ), // 2, has MediaService + Launcher Activity =  CustomMediaSource
        ComponentName("com.test.not.media.package3", "NotMedia"), // 3, Not a MediaService
    )

    // List of MediaServices returned by the PackageManager for queryIntentServices.
    private val mediaServices: List<ResolveInfo> = listOfComponentNames.map { getResolveInfo(it) }

    /**
     * Returns a mocked ResolveInfo
     * @param componentName packageName + className of the mocked [ServiceInfo]
     */
    private fun getResolveInfo(componentName: ComponentName): ResolveInfo {
        return ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = componentName.packageName
                name = componentName.className
            }
        }
    }

    @Test
    fun getAllMediaServices_noCustomComponents_shouldFilterMediaTemplateApps() = scope.runTest {
        ShadowMediaSource.setMediaTemplates(
            listOf(
                listOfComponentNames[0],
                listOfComponentNames[1]
            )
        )
        val packageManager: PackageManager = mock {
            on {
                queryIntentServices(
                    any(), anyInt()
                )
            } doReturn mediaServices
        }
        val mediaAppsDataSource = MediaTemplateAppsDataSourceImpl(
            packageManager,
            appContext,
            bgDispatcher
        )

        val outputMediaServiceInfoList =
            mediaAppsDataSource.getAllMediaServices(includeCustomMediaPackages = false)

        val expectedMediaList =
            mediaServices.filterIndexed { index, _ -> index != 2 && index != 3 }
        assertEquals(expectedMediaList, outputMediaServiceInfoList)
    }

    @Test
    fun getAllMediaServices_includeCustomComponents_shouldFilterMediaTemplateAndCustomApps() =
        scope.runTest {
            ShadowMediaSource.setMediaTemplates(
                listOf(
                    listOfComponentNames[0],
                    listOfComponentNames[1]
                )
            )
            ShadowMediaSource.setCustomTemplates(listOf(listOfComponentNames[2]))
            val packageManager: PackageManager = mock {
                on {
                    queryIntentServices(
                        any(), anyInt()
                    )
                } doReturn mediaServices
            }
            val mediaAppsDataSource = MediaTemplateAppsDataSourceImpl(
                packageManager,
                appContext,
                bgDispatcher
            )

            val outputMediaServiceInfoList =
                mediaAppsDataSource.getAllMediaServices(includeCustomMediaPackages = true)

            val expectedMediaList = mediaServices.dropLast(1)
            assertEquals(expectedMediaList, outputMediaServiceInfoList)
        }
}
