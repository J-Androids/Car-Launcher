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

import android.app.ActivityOptions
import android.car.Car
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.android.car.carlauncher.R
import java.net.URISyntaxException

/**
 * A sealed class representing various ways to launch applications within the system.
 * Subclasses define specialized launch behaviors for different app types (launcher
 * activities, media services, disabled apps, etc.).
 *
 * @see LauncherActivityLaunchProvider
 * @see MediaServiceLaunchProvider
 * @see DisabledAppLaunchProvider
 * @see TosDisabledAppLaunchProvider
 * @see MirroringAppLaunchProvider
 */
sealed class AppLaunchProvider {

    /**
     *  Launches the app associated with the given ComponentName. This is the core
     *  method that all subclasses must implement.
     *
     *  @param context Application context used for launching the activity.
     *  @param componentName The ComponentName identifying the app to launch.
     *  @param launchIntent An optional Intent that might contain additional launch instructions.
     */
    abstract fun launch(
        context: Context,
        componentName: ComponentName,
        launchIntent: Intent? = null
    )

    /**
     * Utility method to launch an Intent with consistent ActivityOptions
     */
    protected fun launchApp(context: Context, intent: Intent) {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = context.display?.displayId ?: 0
        context.startActivity(intent, options.toBundle())
    }

    /**
     * Handles launching standard launcher activities. It constructs an Intent with
     * the necessary flags for launching a new instance of the given app.
     */
    internal object LauncherActivityLaunchProvider : AppLaunchProvider() {
        override fun launch(context: Context, componentName: ComponentName, launchIntent: Intent?) {
            launchApp(
                context,
                Intent(Intent.ACTION_MAIN).setComponent(componentName)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Launches media services, either by directly starting a media center template or
     * switching active media sources. This provider works in conjunction with the CarMediaManager.
     *
     * @param carMediaManager Interface for controlling car media settings.
     * @param launchMediaCenter If true, launches the media center template directly.
     * @param closeScreen A callback function, likely used to close a prior UI screen.
     */
    internal class MediaServiceLaunchProvider(
        private val carMediaManager: CarMediaManager,
        private val launchMediaCenter: Boolean,
        val closeScreen: () -> Unit
    ) : AppLaunchProvider() {
        override fun launch(context: Context, componentName: ComponentName, launchIntent: Intent?) {
            if (launchMediaCenter) {
                launchApp(
                    context,
                    Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE).putExtra(
                        Car.CAR_EXTRA_MEDIA_COMPONENT,
                        componentName.flattenToString()
                    )
                )
                return
            }
            carMediaManager.setMediaSource(componentName, CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)
            closeScreen()
        }
    }

    /**
     * Responsible for enabling and then launching disabled applications. Requires access
     * to the PackageManager to manage application state.
     */
    internal class DisabledAppLaunchProvider(
        private val packageManager: PackageManager,
    ) : AppLaunchProvider() {
        override fun launch(context: Context, componentName: ComponentName, launchIntent: Intent?) {
            packageManager.setApplicationEnabledSetting(
                componentName.packageName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                0
            )
            // Fetch the current enabled setting to make sure the setting is synced
            // before launching the activity. Otherwise, the activity may not
            // launch.
            check(
                packageManager.getApplicationEnabledSetting(componentName.packageName)
                        == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) {
                ("Failed to enable the disabled package [" + componentName.packageName + "]")
            }
            Log.i(TAG, "Successfully enabled package [${componentName.packageName}]")
            launchApp(
                context,
                Intent(Intent.ACTION_MAIN).setComponent(componentName)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        companion object {
            const val TAG = "DisabledAppLaunchProvider"
        }
    }

    /**
     * Handles launching the system activity for Terms of Service (TOS) acceptance. This
     * provider redirects the user to the TOS flow for apps restricted by TOS.
     */
    internal object TosDisabledAppLaunchProvider : AppLaunchProvider() {
        override fun launch(context: Context, componentName: ComponentName, launchIntent: Intent?) {
            val tosIntentName = context.resources.getString(R.string.user_tos_activity_intent)
            try {
                launchApp(context, Intent.parseUri(tosIntentName, Intent.URI_ANDROID_APP_SCHEME))
            } catch (se: URISyntaxException) {
                Log.e(TAG, "Invalid intent URI in user_tos_activity_intent", se)
            }
        }

        const val TAG = "TosDisabledAppLaunchProvider"
    }

    /**
     * Handles app mirroring scenarios. Assumes that a launchIntent is provided with the
     * necessary information to launch the mirrored app.
     */
    internal object MirroringAppLaunchProvider :
        AppLaunchProvider() {
        override fun launch(context: Context, componentName: ComponentName, launchIntent: Intent?) {
            launchIntent?.let {
                launchApp(context, it)
            }
        }
    }
}
