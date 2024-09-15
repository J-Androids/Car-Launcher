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

package com.android.car.docklib.data

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.DockItemProto.DockAppItemListMessage
import com.android.car.docklib.DockItemProto.DockAppItemMessage
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockProtoDataControllerTest {

    private lateinit var dataController: DockProtoDataController
    private val dataSource = mock<DockProtoDataSource>()

    private val pinnedItemA = ComponentName("packageA", "classA")
    private val pinnedItemC = ComponentName("packageC", "classC")

    @Before
    fun setup() {
        dataController = DockProtoDataController(mock<File>())
        val field = DockProtoDataController::class.java.getDeclaredField("dataSource")
        field.isAccessible = true
        field.set(dataController, dataSource)
    }

    @Test
    fun testSaveToFile_OneItem_DataCorrect() {
        val dockData = mapOf(1 to pinnedItemA)
        val captor = argumentCaptor<DockAppItemListMessage>()

        dataController.savePinnedItemsToFile(dockData)

        verify(dataSource).writeToFile(captor.capture())
        assertThat(captor.allValues.size).isEqualTo(1)
        val dockProtoData = captor.allValues[0].dockAppItemMessageList
        assertThat(dockProtoData.size).isEqualTo(dockData.size)
        assertThat(dockProtoData[0].packageName).isEqualTo(pinnedItemA.packageName)
        assertThat(dockProtoData[0].className).isEqualTo(pinnedItemA.className)
        assertThat(dockProtoData[0].relativePosition).isEqualTo(1)
    }

    @Test
    fun testSaveToFile_MultipleItems_DataCorrect() {
        val dockData = mapOf(
            0 to pinnedItemA,
            // Intentionally skip index 1
            2 to pinnedItemC,
        )
        val captor = argumentCaptor<DockAppItemListMessage>()

        dataController.savePinnedItemsToFile(dockData)

        verify(dataSource).writeToFile(captor.capture())
        assertThat(captor.allValues.size).isEqualTo(1)
        val dockProtoData = captor.allValues[0].dockAppItemMessageList
        assertThat(dockProtoData.size).isEqualTo(dockData.size)
        assertThat(dockProtoData[0].packageName).isEqualTo(pinnedItemA.packageName)
        assertThat(dockProtoData[0].className).isEqualTo(pinnedItemA.className)
        assertThat(dockProtoData[0].relativePosition).isEqualTo(0)
        assertThat(dockProtoData[1].packageName).isEqualTo(pinnedItemC.packageName)
        assertThat(dockProtoData[1].className).isEqualTo(pinnedItemC.className)
        assertThat(dockProtoData[1].relativePosition).isEqualTo(2)
    }

    @Test
    fun testLoadFromFile_OneItem_DataCorrect() {
        val dockProtoData = DockAppItemListMessage.newBuilder()
            .addDockAppItemMessage(convertDockItemToProto(pinnedItemA, 1))
            .build()
        whenever(dataSource.readFromFile()).thenReturn(dockProtoData)

        val loadedDockData = dataController.loadFromFile()

        assertThat(loadedDockData?.size).isEqualTo(1)
        assertThat(loadedDockData?.get(1)).isEqualTo(pinnedItemA)
    }

    @Test
    fun testLoadFromFile_MultipleItems_DataCorrect() {
        val dockProtoData = DockAppItemListMessage.newBuilder()
            .addDockAppItemMessage(convertDockItemToProto(pinnedItemA, 0))
            .addDockAppItemMessage(convertDockItemToProto(pinnedItemC, 2))
            .build()
        whenever(dataSource.readFromFile()).thenReturn(dockProtoData)

        val loadedDockData = dataController.loadFromFile()

        assertThat(loadedDockData?.size).isEqualTo(2)
        assertThat(loadedDockData?.get(0)).isEqualTo(pinnedItemA)
        assertThat(loadedDockData?.get(2)).isEqualTo(pinnedItemC)
    }

    private fun convertDockItemToProto(componentName: ComponentName, position: Int) =
        DockAppItemMessage.newBuilder()
            .setPackageName(componentName.packageName)
            .setClassName(componentName.className)
            .setRelativePosition(position)
            .build()
}
