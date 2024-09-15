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
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.view.View
import com.android.car.carlaunchercommon.shortcuts.AppInfoShortcutItem
import com.android.car.carlaunchercommon.shortcuts.ForceStopShortcutItem
import com.android.car.carlaunchercommon.shortcuts.PinShortcutItem
import com.android.car.dockutil.Flags
import com.android.car.dockutil.events.DockEventSenderHelper
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup

/**
 * This class is responsible for creating and displaying app shortcuts popups within the
 * car UI. It generates shortcuts for actions like "Stop App," "App Info," and potentially
 * "Pin to Dock." The class interacts with CarMediaManager and relies on a ShortcutsListener
 * to track interactions with the shortcuts popup.
 *
 * @param carMediaManager For controlling car media settings.
 * @param mediaServiceComponents A set of ComponentNames identifying installed media services.
 * @param shortcutsListener Listener for handling events triggered by the shortcuts popup.
 */
class AppShortcutsFactory(
    private val carMediaManager: CarMediaManager,
    private val mediaServiceComponents: Set<ComponentName>,
    private val shortcutsListener: ShortcutsListener
) {

    /**
     * Displays a car UI shortcuts popup anchored to the provided view.  The popup includes
     * shortcuts for "Force Stop," "App Info," and potentially "Pin to Dock" (if the feature
     * is enabled).
     *
     * @param componentName The ComponentName of the app for which shortcuts are generated.
     * @param displayName The display name of the app.
     * @param context Application context.
     * @param anchorView The UI view to anchor the shortcuts popup.
     */
    fun showShortcuts(
        componentName: ComponentName,
        displayName: CharSequence,
        context: Context,
        anchorView: View
    ) {
        val carUiShortcutsPopupBuilder =
            CarUiShortcutsPopup.Builder()
                .addShortcut(
                    ForceStopShortcutItem(
                        context,
                        componentName.packageName,
                        displayName,
                        carMediaManager,
                        mediaServiceComponents
                    )
                )
                .addShortcut(
                    AppInfoShortcutItem(
                        context,
                        componentName.packageName,
                        UserHandle.getUserHandleForUid(Process.myUid())
                    )
                )
        if (Flags.dockFeature()) {
            carUiShortcutsPopupBuilder
                .addShortcut(buildPinToDockShortcut(componentName, context))
        }
        val carUiShortcutsPopup =
            carUiShortcutsPopupBuilder
                .build(context, anchorView)
        carUiShortcutsPopup.show()
        shortcutsListener.onShortcutsShow(carUiShortcutsPopup)
    }

    /**
     * Helper function to construct a shortcut item for the "Pin to Dock" action
     * within the shortcuts popup.
     *
     * @param componentName ComponentName of the app to be pinned.
     * @param context Application context.
     * @return A CarUiShortcutsPopup.ShortcutItem for the "Pin to Dock" action, or null
     *         if the feature is not enabled.
     */
    private fun buildPinToDockShortcut(
        componentName: ComponentName,
        context: Context
    ): CarUiShortcutsPopup.ShortcutItem? {
        val helper = DockEventSenderHelper(context)
        return PinShortcutItem(
            context.resources,
            false,
            { helper.sendPinEvent(componentName) }
        )
        { helper.sendUnpinEvent(componentName) }
    }

    /**
     *  Simple callback interface for notifying clients when a car UI shortcuts
     *  popup is displayed.
     */
    interface ShortcutsListener {
        /**
         *  Called when a CarUiShortcutsPopup view becomes visible.
         *
         *  @param carUiShortcutsPopup The displayed popup view.
         */
        fun onShortcutsShow(carUiShortcutsPopup: CarUiShortcutsPopup)
    }
}
