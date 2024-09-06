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

import com.android.car.carlauncher.LauncherItemProto
import com.android.car.carlauncher.LauncherItemProto.LauncherItemMessage
import com.android.car.carlauncher.datasources.AppOrderDataSource.AppOrderInfo
import com.android.car.carlauncher.datastore.launcheritem.LauncherItemListSource
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppOrderProtoDataSourceImplTest {

    private val scope = TestScope()
    private val bgDispatcher =
        StandardTestDispatcher(scope.testScheduler, name = "Background dispatcher")

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetSavedAppOrder_noSavedOrder_sendsEmptyList() = scope.runTest {
        val launcherItemListSource: LauncherItemListSource = mock()
        val appOrderDataSource = AppOrderProtoDataSourceImpl(launcherItemListSource, bgDispatcher)
        val flows = mutableListOf<List<AppOrderInfo>>()

        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrder().toList(flows)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        verify(launcherItemListSource).readFromFile()
        assertEquals(emptyList<AppOrderInfo>(), flows[0])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetSavedAppOrder_hasSavedOrder_sendsUpdatedList() = scope.runTest {
        val previouslySavedApp1 = LauncherItemMessage.newBuilder()
            .setPackageName("PackageName1")
            .setClassName("ClassName1")
            .setDisplayName("DisplayName1")
            .setRelativePosition(1)
            .setContainerID(-1).build()
        val previouslySavedApp2 = LauncherItemMessage.newBuilder()
            .setPackageName("PackageName2")
            .setClassName("ClassName2")
            .setDisplayName("DisplayName2")
            .setRelativePosition(2)
            .setContainerID(-1).build()
        val savedProtoAppOrder = listOf(previouslySavedApp1, previouslySavedApp2)
        val expectedAppOrder =
            savedProtoAppOrder.map {
                AppOrderInfo(
                    it.packageName,
                    it.className,
                    it.displayName
                )
            }
        val launcherItemListSource: LauncherItemListSource = mock {
            on { readFromFile() } doReturn
                    LauncherItemProto.LauncherItemListMessage.newBuilder()
                        .addAllLauncherItemMessage(savedProtoAppOrder).build()
        }
        val appOrderDataSource = AppOrderProtoDataSourceImpl(launcherItemListSource, bgDispatcher)
        val flows = mutableListOf<List<AppOrderInfo>>()

        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrder().toList(flows)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        verify(launcherItemListSource).readFromFile()
        assertEquals(expectedAppOrder, flows[0])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testSavedAppOrder_savesPersistently_reportsChangeToCollectors() = scope.runTest {
        val newAppInfo1 = AppOrderInfo("PackageName1", "ClassName1", "DisplayName1")
        val newAppInfo2 = AppOrderInfo("PackageName2", "ClassName2", "DisplayName2")
        val newSaveOrder = listOf(newAppInfo1, newAppInfo2)
        val expectedProtoAppOrder = newSaveOrder.mapIndexed { index, it ->
            LauncherItemMessage.newBuilder()
                .setPackageName(it.packageName)
                .setClassName(it.className)
                .setDisplayName(it.displayName)
                .setRelativePosition(index)
                .setContainerID(-1).build()
        }
        val expectedAppOrderProtoMessage = LauncherItemProto.LauncherItemListMessage.newBuilder()
            .addAllLauncherItemMessage(expectedProtoAppOrder).build()
        val launcherItemListSource: LauncherItemListSource = mock()
        val appOrderDataSource = AppOrderProtoDataSourceImpl(launcherItemListSource, bgDispatcher)
        val savedOrderFlows = mutableListOf<List<AppOrderInfo>>()
        val savedOrderComparatorFlows = mutableListOf<Comparator<AppOrderInfo>>()
        // collect flows to listen for updates.
        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrder().toList(savedOrderFlows)
        }
        advanceUntilIdle()
        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrderComparator().toList(savedOrderComparatorFlows)
        }
        advanceUntilIdle()

        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.saveAppOrder(newSaveOrder)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(2, savedOrderFlows.size)
        assertEquals(2, savedOrderComparatorFlows.size)
        verify(launcherItemListSource, times(2)).readFromFile()
        verify(launcherItemListSource).writeToFile(expectedAppOrderProtoMessage)
        // initial empty app order.
        assertEquals(emptyList<AppOrderInfo>(), savedOrderFlows[0])
        // app order after newly saved order.
        assertEquals(newSaveOrder, savedOrderFlows[1])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetSavedAppOrderComparator_sortsAppsWithSavedOrder() = scope.runTest {
        val previouslySavedApp1 = LauncherItemMessage.newBuilder()
            .setPackageName("PackageName1")
            .setClassName("ClassName1")
            .setDisplayName("DisplayName1")
            .setRelativePosition(1)
            .setContainerID(-1).build()
        val previouslySavedApp2 = LauncherItemMessage.newBuilder()
            .setPackageName("PackageName2")
            .setClassName("ClassName2")
            .setDisplayName("DisplayName2")
            .setRelativePosition(2)
            .setContainerID(-1).build()
        val savedProtoAppOrder = listOf(previouslySavedApp1, previouslySavedApp2)
        val launcherItemListSource: LauncherItemListSource = mock {
            on { readFromFile() } doReturn
                    LauncherItemProto.LauncherItemListMessage.newBuilder()
                        .addAllLauncherItemMessage(savedProtoAppOrder).build()
        }
        // mix of known and unknown app order
        val appToBeSorted1 = AppOrderInfo("PackageName4", "ClassName4", "DisplayName4")
        val appToBeSorted2 = AppOrderInfo("PackageName2", "ClassName2", "DisplayName2")
        val appToBeSorted3 = AppOrderInfo("PackageName1", "ClassName1", "DisplayName1")
        val appToBeSorted4 = AppOrderInfo("PackageName3", "ClassName3", "DisplayName3")
        // Unsorted list of apps applied with the comparator under test.
        val appsTobeSorted = listOf(appToBeSorted1, appToBeSorted2, appToBeSorted3, appToBeSorted4)
        // The above list is expected to be sorted as below. Apps with unknown app order are sorted
        // by AppOrderInfo#displayName.
        val expectedOrder = listOf(appToBeSorted3, appToBeSorted2, appToBeSorted4, appToBeSorted1)
        val appOrderDataSource = AppOrderProtoDataSourceImpl(launcherItemListSource, bgDispatcher)
        val flows = mutableListOf<Comparator<AppOrderInfo>>()

        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrderComparator().toList(flows)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        verify(launcherItemListSource).readFromFile()
        assertEquals(expectedOrder, appsTobeSorted.sortedWith(flows[0]))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testClearAppOrder_clearsAppOrder_reportsUpdateToCollectors() = scope.runTest {
        val previouslySavedApp1 = LauncherItemMessage.newBuilder()
            .setPackageName("PackageName1")
            .setClassName("ClassName1")
            .setDisplayName("DisplayName1")
            .setRelativePosition(1)
            .setContainerID(-1).build()
        val savedProtoAppOrder = listOf(previouslySavedApp1)
        val expectedAppOrder = savedProtoAppOrder.map {
            AppOrderInfo(
                it.packageName,
                it.className,
                it.displayName
            )
        }
        val launcherItemListSource: LauncherItemListSource = mock {
            on { readFromFile() } doReturn
                    LauncherItemProto.LauncherItemListMessage.newBuilder()
                        .addAllLauncherItemMessage(savedProtoAppOrder).build()
            on { deleteFile() } doReturn true
        }
        val appOrderDataSource = AppOrderProtoDataSourceImpl(launcherItemListSource, bgDispatcher)
        // collect flows to listen for updates.
        val savedOrderFlows = mutableListOf<List<AppOrderInfo>>()
        val savedOrderComparatorFlows = mutableListOf<Comparator<AppOrderInfo>>()
        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrder().toList(savedOrderFlows)
        }
        advanceUntilIdle()
        launch(StandardTestDispatcher(testScheduler)) {
            appOrderDataSource.getSavedAppOrderComparator().toList(savedOrderComparatorFlows)
        }
        advanceUntilIdle()

        appOrderDataSource.clearAppOrder()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(2, savedOrderFlows.size)
        assertEquals(2, savedOrderComparatorFlows.size)
        verify(launcherItemListSource).deleteFile()
        assertEquals(expectedAppOrder, savedOrderFlows[0])
        assertEquals(expectedAppOrder, savedOrderFlows[0])
        assertEquals(emptyList<AppOrderInfo>(), savedOrderFlows[1])
        assertEquals(emptyList<AppOrderInfo>(), savedOrderFlows[1])
    }
}
