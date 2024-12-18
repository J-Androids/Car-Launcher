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
package com.android.car.carlauncher;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.car.media.common.source.MediaModels;
import com.android.car.media.common.source.MediaSessionHelper;

/** Utility class that handles common MediaSession related logic*/
public class MediaSessionUtils {
    private static final String TAG = "MediaSessionUtils";

    private MediaSessionUtils() {}

    /** Create a MediaModels object */
    public static MediaModels getMediaModels(Context context) {
        return new MediaModels(context.getApplicationContext(),
                createNotificationProvider(context));
    }

    /** Create a MediaSessionHelper object */
    public static MediaSessionHelper getMediaSessionHelper(Context context) {
        return new MediaSessionHelper(context.getApplicationContext(),
                createNotificationProvider(context));
    }

    private static MediaSessionHelper.NotificationProvider createNotificationProvider(
            Context context) {
        return new MediaSessionHelper.NotificationProvider() {
            @Override
            public StatusBarNotification[] getActiveNotifications() {
                try {
                    return NotificationManager.getService()
                            .getActiveNotificationsWithAttribution(
                                    context.getPackageName(), null);
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception trying to get active notifications " + e);
                    return new StatusBarNotification[0];
                }
            }

            @Override
            public boolean isMediaNotification(Notification notification) {
                return notification.isMediaNotification();
            }
        };
    }
}
