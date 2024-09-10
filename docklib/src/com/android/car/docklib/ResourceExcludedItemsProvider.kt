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

package com.android.car.docklib

import android.content.ComponentName
import android.content.Context

/**
 * [ExcludedItemsProvider] that reads from resources and excludes given packages and components.
 */
class ResourceExcludedItemsProvider(context: Context) : ExcludedItemsProvider {
    private val excludedPackages = context.resources
            .getStringArray(R.array.config_packagesExcludedFromDock).toHashSet()
    private val excludedComponents = context.resources
            .getStringArray(R.array.config_componentsExcludedFromDock)
            .mapNotNull(ComponentName::unflattenFromString).toHashSet()

    override fun isPackageExcluded(pkg: String) = excludedPackages.contains(pkg)

    override fun isComponentExcluded(component: ComponentName) =
        excludedComponents.contains(component)
}
