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

package com.android.car.docklib.view

import android.view.View
import com.android.car.carlaunchercommon.toasts.NonDrivingOptimizedLaunchFailedToast.Companion.showToast
import com.android.car.docklib.DockInterface
import com.android.car.docklib.data.DockAppItem

/**
 * [View.OnClickListener] for handling clicks on dock item.
 *
 * @property isRestricted if the item is restricted
 */
class DockItemClickListener(
    private val dockController: DockInterface,
    private val dockAppItem: DockAppItem,
    private var isRestricted: Boolean,
) : View.OnClickListener {
    override fun onClick(v: View?) {
        if (isRestricted) {
            v?.context?.let { showToast(it, dockAppItem.name) }
            return
        }
        dockController.launchApp(dockAppItem.component, dockAppItem.isMediaApp)
    }

    /**
     * Set if the item is restricted.
     */
    fun setIsRestricted(isRestricted: Boolean) {
        this.isRestricted = isRestricted
    }
}
