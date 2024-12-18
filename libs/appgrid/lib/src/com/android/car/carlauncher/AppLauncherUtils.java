/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.car.settings.CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS;
import static android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED;

import static com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.TOS_DISABLED_APPS_SEPARATOR;
import static com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.TOS_NOT_ACCEPTED;
import static com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.TOS_UNINITIALIZED;

import android.app.ActivityOptions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Util class that contains helper method used by app launcher classes.
 */
public class AppLauncherUtils {
    private static final String TAG = "AppLauncherUtils";

    private AppLauncherUtils() {
    }

    /**
     * Helper method that launches the app given the app's AppMetaData.
     */
    public static void launchApp(Context context, Intent intent) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(context.getDisplay().getDisplayId());
        context.startActivity(intent, options.toBundle());
    }

    /**
     * Gets the intent for launching the TOS acceptance flow
     *
     * @param context The app context
     * @return TOS intent, or null
     */
    @Nullable
    public static Intent getIntentForTosAcceptanceFlow(Context context) {
        String tosIntentName =
                context.getResources().getString(R.string.user_tos_activity_intent);
        try {
            return Intent.parseUri(tosIntentName, Intent.URI_ANDROID_APP_SCHEME);
        } catch (URISyntaxException se) {
            Log.e(TAG, "Invalid intent URI in user_tos_activity_intent", se);
            return null;
        }
    }

    /**
     * Returns a set of packages that are disabled by tos
     *
     * @param context The application context
     * @return Set of packages disabled by tos
     */
    public static Set<String> getTosDisabledPackages(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(contentResolverForUser,
                KEY_UNACCEPTED_TOS_DISABLED_APPS);
        return TextUtils.isEmpty(settingsValue) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsValue.split(
                        TOS_DISABLED_APPS_SEPARATOR)));
    }

    /**
     * Check if a user has accepted TOS
     *
     * @param context The application context
     * @return true if the user has accepted Tos, false otherwise
     */
    public static boolean tosAccepted(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(
                contentResolverForUser,
                KEY_USER_TOS_ACCEPTED);
        return !Objects.equals(settingsValue, TOS_NOT_ACCEPTED);
    }

    /**
     * Check if TOS status is uninitialized
     *
     * @param context The application context
     * @return true if tos is uninitialized, false otherwise
     */
    static boolean tosStatusUninitialized(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(
                contentResolverForUser,
                KEY_USER_TOS_ACCEPTED);
        return Objects.equals(settingsValue, TOS_UNINITIALIZED);
    }
}
