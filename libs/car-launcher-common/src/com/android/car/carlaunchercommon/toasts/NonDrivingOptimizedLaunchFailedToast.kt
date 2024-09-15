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

package com.android.car.carlaunchercommon.toasts

import android.content.Context
import android.widget.Toast
import com.android.car.carlaunchercommon.R

/**
 * Helper to create and show a [Toast] when user taps on a non driving optimized app when
 * UX restrictions are in effect.
 */
class NonDrivingOptimizedLaunchFailedToast {
    companion object {
        /**
         * Show the NDO launch fail toast
         *
         * @param displayName app's display name/label
         */
        fun showToast(context: Context, displayName: String) {
            getToast(context, displayName).show()
        }

        private fun getToast(context: Context, displayName: String): Toast {
            val warningText: String = context.getResources()
                .getString(R.string.ndo_launch_fail_toast_text, displayName)

            return Toast.makeText(context, warningText, Toast.LENGTH_LONG)
        }
    }
}
