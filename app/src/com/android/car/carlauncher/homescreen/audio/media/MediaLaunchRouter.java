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

package com.android.car.carlauncher.homescreen.audio.media;

import com.android.car.carlauncher.homescreen.audio.MediaLaunchHandler;
import com.android.car.media.common.source.MediaSource;

/**
 * Routes media launches to {@link MediaLaunchHandler}.
 */
public class MediaLaunchRouter {
    private static MediaLaunchRouter sInstance;
    private MediaLaunchHandler mMediaLaunchHandler;

    /**
     * @return an instance of {@link MediaLaunchRouter}.
     */
    public static MediaLaunchRouter getInstance() {
        if (sInstance == null) {
            sInstance = new MediaLaunchRouter();
        }
        return sInstance;
    }

    /**
     * Register a {@link MediaLaunchHandler}.
     */
    public void registerMediaLaunchHandler(MediaLaunchHandler mediaLaunchHandler) {
        mMediaLaunchHandler = mediaLaunchHandler;
    }

    /**
     * Dispatch a media source to {@link MediaLaunchHandler}
     */
    public void handleLaunchMedia(MediaSource mediaSource) {
        if (mediaSource != null) {
            mMediaLaunchHandler.handleLaunchMedia(mediaSource);
        }
    }
}
