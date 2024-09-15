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

import android.util.Log
import com.android.car.carlauncher.LauncherItemProto
import com.android.car.carlauncher.LauncherItemProto.LauncherItemMessage
import com.android.car.carlauncher.datasources.AppOrderDataSource.AppOrderInfo
import com.android.car.carlauncher.datastore.launcheritem.LauncherItemListSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * DataSource for managing the persisted order of apps. This class encapsulates all
 * interactions with the persistent storage (e.g., Files, Proto, Database), acting as
 * the single source of truth.
 *
 * Important: To ensure consistency, avoid modifying the persistent storage directly.
 *            Use the methods provided by this DataSource.
 */
interface AppOrderDataSource {

    /**
     * Saves the provided app order to persistent storage.
     *
     * @param appOrderInfoList The new order of apps to be saved, represented as a list of
     * LauncherItemMessage objects.
     */
    suspend fun saveAppOrder(appOrderInfoList: List<AppOrderInfo>)

    /**
     * Returns a Flow of the saved app order. The Flow will emit the latest saved order
     * and any subsequent updates.
     *
     * @return A Flow of [AppOrderInfo] lists, representing the saved app order.
     */
    fun getSavedAppOrder(): Flow<List<AppOrderInfo>>

    /**
     * Returns a Flow of comparators for sorting app lists. The comparators will prioritize the
     * saved app order, and may fall back to other sorting logic if necessary.
     *
     * @return A Flow of Comparator objects, used to sort [AppOrderInfo] lists.
     */
    fun getSavedAppOrderComparator(): Flow<Comparator<AppOrderInfo>>

    /**
     * Clears the saved app order from persistent storage.
     *
     * @return `true` if the operation was successful, `false` otherwise.
     */
    suspend fun clearAppOrder(): Boolean

    data class AppOrderInfo(val packageName: String, val className: String, val displayName: String)
}

/**
 * Implementation of the [AppOrderDataSource] interface, responsible for managing app order
 * persistence using a Proto file storage mechanism.
 *
 * @property launcherItemListSource The source for accessing and updating the raw Proto data.
 * @property bgDispatcher (Optional) A CoroutineDispatcher specifying the thread pool for background
 *                      operations (defaults to Dispatchers.IO for I/O-bound tasks).
 */
class AppOrderProtoDataSourceImpl(
    private val launcherItemListSource: LauncherItemListSource,
    private val bgDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppOrderDataSource {

    private val appOrderFlow = MutableStateFlow(emptyList<AppOrderInfo>())

    /**
     * Saves the current app order to a Proto file for persistent storage.
     * * Performs the save operation on the background dispatcher ([bgDispatcher]).
     * * Updates all collectors of [getSavedAppOrderComparator] and [getSavedAppOrder] immediately,
     *   even before the write operation has completed.
     * * In case of a write failure, the operation fails silently. This might lead to a
     *   temporarily inconsistent app order for the current session (until the app restarts).
     */
    override suspend fun saveAppOrder(appOrderInfoList: List<AppOrderInfo>) {
        // Immediately update the cache.
        appOrderFlow.value = appOrderInfoList
        // Store the app order persistently.
        withContext(bgDispatcher) {
            // If it fails to write, it fails silently.
            if (!launcherItemListSource.writeToFile(
                    LauncherItemProto.LauncherItemListMessage.newBuilder()
                        .addAllLauncherItemMessage(appOrderInfoList.mapIndexed { index, item ->
                            convertToMessage(item, index)
                        }).build()
                )
            ) {
                Log.i(TAG, "saveAppOrder failed to writeToFile")
            }
        }
    }

    /**
     * Gets the latest know saved order to sort the apps.
     * Also check [getSavedAppOrderComparator] if you need comparator to sort the list of apps.
     *
     * * Emits a new list to all collectors whenever the app order is updated using the
     *   [saveAppOrder] function or when [clearAppOrder] is called.
     *
     * __Handling Apps with Unknown Positions:__
     * The client should implement logic to handle apps whose positions are not
     * specified in the saved order. A common strategy is to append them to the end of the list.
     *
     * __Handling Unavailable Apps:__
     * The client can choose to exclude apps that are unavailable (e.g., uninstalled or disabled)
     * from the sorted list.
    */
    override fun getSavedAppOrder(): Flow<List<AppOrderInfo>> = flow {
        withContext(bgDispatcher) {
            val appOrderFromFiles = launcherItemListSource.readFromFile()?.launcherItemMessageList
            // Read from the persistent storage for pre-existing order.
            // If no pre-existing order exists it initially returns an emptyList.
            if (!appOrderFromFiles.isNullOrEmpty()) {
                appOrderFlow.value =
                    appOrderFromFiles.sortedBy { it.relativePosition }
                        .map { AppOrderInfo(it.packageName, it.className, it.displayName) }
            }
        }
        emitAll(appOrderFlow)
    }.flowOn(bgDispatcher)

    /**
     * Provides a Flow of comparators to sort a list of apps.
     *
     * * Sorts apps based on a pre-defined order. If an app is not found in the pre-defined
     *   order, it falls back to alphabetical sorting with [AppOrderInfo.displayName].
     * * Emits a new comparator to all collectors whenever the app order is updated using the
     *   [saveAppOrder] function or when [clearAppOrder] is called.
     *
     * @see getSavedAppOrder
     */
    override fun getSavedAppOrderComparator(): Flow<Comparator<AppOrderInfo>> {
        return getSavedAppOrder().map { appOrderInfoList ->
            val appOrderMap = appOrderInfoList.withIndex().associateBy({it.value}, {it.index})
            Comparator<AppOrderInfo> { app1, app2 ->
                when {
                    // Both present in predefined list.
                    appOrderMap.contains(app1) && appOrderMap.contains(app2) -> {
                        // Kotlin compiler complains for nullability, although this should not be.
                        appOrderMap[app1]!! - appOrderMap[app2]!!
                    }
                    // Prioritize predefined names.
                    appOrderMap.contains(app1) -> -1
                    appOrderMap.contains(app2) -> 1
                    // Fallback to alphabetical.
                    else -> app1.displayName.compareTo(app2.displayName)
                }
            }
        }.flowOn(bgDispatcher)
    }

    /**
     * Deletes the persisted app order data. Performs the file deletion operation on the
     * background dispatcher ([bgDispatcher]).
     *
     * * Successful deletion will report empty/default order [emptyList] to collectors of
     *   [getSavedAppOrder] amd [getSavedAppOrderComparator]
     *
     * @return `true` if the deletion was successful, `false` otherwise.
     */
    override suspend fun clearAppOrder(): Boolean {
        return withContext(bgDispatcher) {
            launcherItemListSource.deleteFile()
        }.also {
            if (it) {
                // If delete is successful report empty app order.
                appOrderFlow.value = emptyList()
            }
        }
    }

    private fun convertToMessage(
        appOrderInfo: AppOrderInfo,
        relativePosition: Int
    ): LauncherItemMessage? {
        val builder = LauncherItemMessage.newBuilder().setPackageName(appOrderInfo.packageName)
            .setClassName(appOrderInfo.className).setDisplayName(appOrderInfo.displayName)
            .setRelativePosition(relativePosition).setContainerID(DOES_NOT_SUPPORT_CONTAINER)
        return builder.build()
    }

    companion object {
        val TAG: String = AppOrderDataSource::class.java.simpleName
        private const val DOES_NOT_SUPPORT_CONTAINER = -1
    }
}
