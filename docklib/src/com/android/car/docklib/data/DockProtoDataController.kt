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
import android.os.Build
import android.util.Log
import com.android.car.docklib.DockItemProto.DockAppItemListMessage
import com.android.car.docklib.DockItemProto.DockAppItemMessage
import java.io.File

/**
 * Proto file controller to read and write to store dock data
 * @param dataFile a file that the current user has read and write permission
 */
class DockProtoDataController(dataFile: File) {
    companion object {
        private const val TAG = "DockProtoDataController"
        private val DEBUG = Build.isDebuggable()
        const val FILE_NAME = "dock_item_data"
    }
    private val dataSource = DockProtoDataSource(dataFile)

    /**
     * Load data from storage file
     * @return mapping of a position to the component pinned to that position,
     * or an empty mapping if the storage file doesn't exist
     */
    fun loadFromFile(): Map<Int, ComponentName>? {
        if (DEBUG) Log.d(TAG, "Loading dock from file $dataSource")
        return dataSource.readFromFile()?.let { dockAppItemListMessage ->
            val items = HashMap<Int, ComponentName>()
            dockAppItemListMessage.dockAppItemMessageList.forEach {
                val componentName = ComponentName(it.packageName, it.className)
                items[it.relativePosition] = componentName
            }
            if (DEBUG) Log.d(TAG, "Loaded dock from file $dataSource")
            items
        }
    }

    /**
     * Create and write pinned dock items to file
     * @param pinnedDockItems mapping of position to the component pinned to that position
     */
    fun savePinnedItemsToFile(pinnedDockItems: Map<Int, ComponentName>) {
        if (DEBUG) Log.d(TAG, "Save dock to file $dataSource")
        val data = DockAppItemListMessage.newBuilder()
        pinnedDockItems.forEach() {
            data.addDockAppItemMessage(
                DockAppItemMessage.newBuilder()
                        .setPackageName(it.value.packageName)
                        .setClassName(it.value.className)
                        .setRelativePosition(it.key)
                        .build()
            )
        }
        dataSource.writeToFile(data.build())
    }
}
