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

package shadows

import android.content.ComponentName
import android.content.Context
import com.android.car.media.common.source.MediaSource
import org.robolectric.annotation.Implements

@Implements(MediaSource::class)
class ShadowMediaSource {
    companion object {
        private val mediaTemplateComponents = mutableSetOf<ComponentName>()
        private val customComponents = mutableSetOf<ComponentName>()

        @JvmStatic
        fun isMediaTemplate(context: Context, mbsComponentName: ComponentName): Boolean {
            return mediaTemplateComponents.contains(mbsComponentName)
        }

        @JvmStatic
        fun isAudioMediaSource(context: Context, mbsComponentName: ComponentName): Boolean {
            return customComponents.contains(mbsComponentName) ||
                    mediaTemplateComponents.contains(mbsComponentName)
        }

        fun setMediaTemplates(mediaTemplateComponents: List<ComponentName>) {
            this.mediaTemplateComponents.clear()
            this.mediaTemplateComponents.addAll(mediaTemplateComponents)
        }

        fun setCustomTemplates(customComponents: List<ComponentName>) {
            this.customComponents.clear()
            this.customComponents.addAll(customComponents)
        }
    }
}
