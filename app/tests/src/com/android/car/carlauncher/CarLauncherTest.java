/*
 * Copyright (C) 2020 Google Inc.
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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.hasWindowLayoutParams;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;

import android.car.app.RemoteCarTaskView;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.testing.TestableContext;
import android.util.ArraySet;
import android.view.WindowManager;

import androidx.lifecycle.Lifecycle;
import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URISyntaxException;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarLauncherTest extends AbstractExtendedMockitoTestCase {

    @Rule
    public TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
    private ActivityScenario<CarLauncher> mActivityScenario;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TOS_MAP_INTENT = "intent:#Intent;"
            + "component=com.android.car.carlauncher/"
            + "com.android.car.carlauncher.homescreen.MapActivityTos;"
            + "action=android.intent.action.MAIN;end";
    private static final String DEFAULT_MAP_INTENT = "intent:#Intent;"
            + "component=com.android.car.maps/"
            + "com.android.car.maps.MapActivity;"
            + "action=android.intent.action.MAIN;end";
    private static final String CUSTOM_MAP_INTENT = "intent:#Intent;component=com.custom.car.maps/"
            + "com.custom.car.maps.MapActivity;"
            + "action=android.intent.action.MAIN;end";
    // TOS disabled app list is non empty when TOS is not accepted.
    private static final String NON_EMPTY_TOS_DISABLED_APPS =
            "com.test.package1, com.test.package2";
    // TOS disabled app list is empty when TOS has been accepted or uninitialized.
    private static final String EMPTY_TOS_DISABLED_APPS = "";

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AppLauncherUtils.class);
        session.spyStatic(CarLauncherUtils.class);
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void onResume_mapsCard_isVisible() {
        setUpActivityScenario();

        onView(withId(R.id.maps_card))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void onResume_assistiveCard_isVisible() {
        setUpActivityScenario();

        onView(withId(R.id.top_card))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void onResume_fullscreenMediaCard_assistiveCard_isGone() {
        setUpActivityScenario();

        onView(withId(R.id.top_card))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void onResume_audioCard_isVisible() {
        setUpActivityScenario();

        onView(withId(R.id.bottom_card))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
    }

    @Test
    public void onCreate_tosMapActivity_tosUnaccepted_canvasOptimizedMapsDisabledByTos() {
        doReturn(false).when(() -> AppLauncherUtils.tosAccepted(any()));
        doReturn(true)
                        .when(() ->
                                CarLauncherUtils.isSmallCanvasOptimizedMapIntentConfigured(any()));
        doReturn(createIntentFromString(TOS_MAP_INTENT))
                .when(() -> CarLauncherUtils.getTosMapIntent(any()));
        doReturn(createIntentFromString(DEFAULT_MAP_INTENT))
                .when(() -> CarLauncherUtils.getSmallCanvasOptimizedMapIntent(any()));
        doReturn(tosDisabledPackages())
                .when(() -> AppLauncherUtils.getTosDisabledPackages(any()));

        mActivityScenario = ActivityScenario.launch(CarLauncher.class);

        mActivityScenario.onActivity(activity -> {
            Intent mapIntent = activity.getMapsIntent();
            // If TOS is not accepted, and the default map is disabled by TOS, or
            // package name maybe null when resolving intent from package manager.
            // We replace the map intent with TOS map activity
            assertEquals(createIntentFromString(TOS_MAP_INTENT).getComponent().getClassName(),
                    mapIntent.getComponent().getClassName());
        });
    }

    @Test
    public void onCreate_tosMapActivity_tosUnaccepted_mapsNotDisabledByTos() {
        doReturn(false).when(() -> AppLauncherUtils.tosAccepted(any()));
        doReturn(true)
                .when(() -> CarLauncherUtils.isSmallCanvasOptimizedMapIntentConfigured(any()));
        doReturn(createIntentFromString(CUSTOM_MAP_INTENT))
                .when(() -> CarLauncherUtils.getSmallCanvasOptimizedMapIntent(any()));
        doReturn(tosDisabledPackages())
                .when(() -> AppLauncherUtils.getTosDisabledPackages(any()));

        mActivityScenario = ActivityScenario.launch(CarLauncher.class);

        mActivityScenario.onActivity(activity -> {
            Intent mapIntent = activity.getMapsIntent();
            // If TOS is not accepted, and the default map is not disabled by TOS,
            // these can be some other navigation app set as default,
            // package name will not be null.
            // We will not replace the map intent with TOS map activity
            assertEquals(
                    createIntentFromString(CUSTOM_MAP_INTENT).getComponent().getClassName(),
                    mapIntent.getComponent().getClassName());
        });
    }

    @Test
    public void onCreate_tosMapActivity_tosAccepted() {
        doReturn(true).when(() -> AppLauncherUtils.tosAccepted(any()));
        doReturn(createIntentFromString(TOS_MAP_INTENT))
                .when(() -> CarLauncherUtils.getTosMapIntent(any()));

        mActivityScenario = ActivityScenario.launch(CarLauncher.class);

        mActivityScenario.onActivity(activity -> {
            Intent mapIntent = activity.getMapsIntent();
            // If TOS is accepted, map intent is not replaced
            assertNotEquals("com.android.car.carlauncher.homescreen.MapActivityTos",
                    mapIntent.getComponent().getClassName());
        });
    }

    @Test
    public void onCreate_whenTosAccepted_tosContentObserverIsNull() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        mActivityScenario.onActivity(activity -> {
            // Content observer not setup because tos is accepted
            assertNull(activity.mTosContentObserver);
        });
    }

    @Test
    public void onCreate_whenTosNotAccepted_tosContentObserverIsNotNull() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                NON_EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        mActivityScenario.onActivity(activity -> {
            // Content observer is setup because tos is not accepted
            assertNotNull(activity.mTosContentObserver);
        });
    }

    @Test
    public void onCreate_whenTosNotInitialized_tosContentObserverIsNotNull() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        mActivityScenario.onActivity(activity -> {
            // Content observer is setup because tos is not initialized
            assertNotNull(activity.mTosContentObserver);
        });
    }

    @Test
    public void recreate_afterTosIsAccepted_tosStateContentObserverIsNull() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                NON_EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup

            // Accept TOS
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);
            Settings.Secure.putString(mContext.getContentResolver(),
                    KEY_UNACCEPTED_TOS_DISABLED_APPS, EMPTY_TOS_DISABLED_APPS);
            activity.mTosContentObserver.onChange(true);
        });

        // Content observer is null after recreate
        mActivityScenario.onActivity(activity -> assertNull(activity.mTosContentObserver));
    }

    @Test
    public void recreate_afterTosIsInitialized_tosStateContentObserverIsNotNull() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup

            // Initialize TOS
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
            Settings.Secure.putString(mContext.getContentResolver(),
                    KEY_UNACCEPTED_TOS_DISABLED_APPS, NON_EMPTY_TOS_DISABLED_APPS);
            activity.mTosContentObserver.onChange(true);
        });

        // Content observer is not null after recreate
        mActivityScenario.onActivity(activity -> assertNotNull(activity.mTosContentObserver));
    }

    @Test
    public void recreate_afterTosIsInitialized_releaseTaskView() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mCarLauncherViewModel); // CarLauncherViewModel is setup

            RemoteCarTaskView oldRemoteCarTaskView =
                    activity.mCarLauncherViewModel.getRemoteCarTaskView().getValue();
            assertNotNull(oldRemoteCarTaskView);

            // Initialize TOS
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
            Settings.Secure.putString(mContext.getContentResolver(),
                    KEY_UNACCEPTED_TOS_DISABLED_APPS, NON_EMPTY_TOS_DISABLED_APPS);
            activity.mTosContentObserver.onChange(true);

            // Different instance of task view since TOS has gone from uninitialized to initialized
            assertThat(oldRemoteCarTaskView).isNotSameInstanceAs(
                    activity.mCarLauncherViewModel.getRemoteCarTaskView().getValue());
        });
    }

    @Test
    public void recreate_afterTosIsAccepted_releaseTaskView() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
        Settings.Secure.putString(mContext.getContentResolver(), KEY_UNACCEPTED_TOS_DISABLED_APPS,
                NON_EMPTY_TOS_DISABLED_APPS);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mCarLauncherViewModel); // CarLauncherViewModel is setup

            RemoteCarTaskView oldRemoteCarTaskView =
                    activity.mCarLauncherViewModel.getRemoteCarTaskView().getValue();
            assertNotNull(oldRemoteCarTaskView);

            // Accept TOS
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);
            Settings.Secure.putString(mContext.getContentResolver(),
                    KEY_UNACCEPTED_TOS_DISABLED_APPS, EMPTY_TOS_DISABLED_APPS);
            activity.mTosContentObserver.onChange(true);

            // Different instance of task view since TOS has been accepted
            assertThat(oldRemoteCarTaskView).isNotSameInstanceAs(
                    activity.mCarLauncherViewModel.getRemoteCarTaskView().getValue());
        });
    }

    private Intent createIntentFromString(String intentString) {
        try {
            return Intent.parseUri(intentString, Intent.URI_ANDROID_APP_SCHEME);
        } catch (URISyntaxException se) {
            return null;
        }
    }

    private Set<String> tosDisabledPackages() {
        Set<String> packages = new ArraySet<>();
        packages.add("com.android.car.maps");
        packages.add("com.android.car.assistant");
        return packages;
    }

    private void setUpActivityScenario() {
        mActivityScenario = ActivityScenario.launch(CarLauncher.class);
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);
        mActivityScenario.onActivity(activity -> {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                }
            });
        });
    }
}
