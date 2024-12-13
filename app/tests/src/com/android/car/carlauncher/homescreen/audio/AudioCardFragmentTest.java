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

package com.android.car.carlauncher.homescreen.audio;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.hasWindowLayoutParams;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.apps.common.CrossfadeImageView;
import com.android.car.carlauncher.CarLauncher;
import com.android.car.carlauncher.Flags;
import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.audio.dialer.DialerCardFragment;
import com.android.car.carlauncher.homescreen.audio.media.MediaCardFragment;
import com.android.car.carlauncher.homescreen.ui.CardContent.CardBackgroundImage;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextView;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;
import com.android.car.carlauncher.homescreen.ui.TextBlockView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AudioCardFragmentTest {

    private static final CardHeader CARD_HEADER = new CardHeader("Test App Name", null);
    private static final BitmapDrawable BITMAP = new BitmapDrawable(
            Bitmap.createBitmap(/* width = */100, /* height = */100, Bitmap.Config.ARGB_8888));
    private static final CardBackgroundImage CARD_BACKGROUND_IMAGE = new CardBackgroundImage(
            BITMAP, null);
    private static final String AUDIO_VIEW_TITLE = "Test song title";
    private static final String AUDIO_VIEW_SUBTITLE = "Test artist name";
    private static final long AUDIO_START_TIME = 1L;
    private static final DescriptiveTextView DESCRIPTIVE_TEXT_VIEW = new DescriptiveTextView(
            /* image = */ null, "Primary Text", "Secondary Text");
    private static final TextBlockView TEXT_BLOCK_VIEW = new TextBlockView("Text");

    private final DescriptiveTextWithControlsView
            mDescriptiveTextWithControlsView = new DescriptiveTextWithControlsView(
            CARD_BACKGROUND_IMAGE,
            AUDIO_VIEW_TITLE,
            AUDIO_VIEW_SUBTITLE);
    private final DescriptiveTextWithControlsView.Control mControl =
            new DescriptiveTextWithControlsView.Control(BITMAP, v -> {
            });
    private final DescriptiveTextWithControlsView
            mDescriptiveTextWithControlsViewWithButtons = new DescriptiveTextWithControlsView(
            CARD_BACKGROUND_IMAGE, AUDIO_VIEW_TITLE, AUDIO_VIEW_SUBTITLE, AUDIO_START_TIME,
            mControl, mControl, mControl);

    private FragmentActivity mActivity;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public ActivityTestRule<CarLauncher> mActivityTestRule =
            new ActivityTestRule<CarLauncher>(CarLauncher.class);

    @Before
    public void setUp() {
        mActivity = mActivityTestRule.getActivity();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            }
        });
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void updateContentAndHeaderView_noControls_showsMediaPlaybackControlsBar_hidesDialer() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        mActivity.runOnUiThread(fragment::showMediaCard);
        MediaCardFragment mediaCardFragment = (MediaCardFragment) fragment.getMediaFragment();

        mediaCardFragment.updateHeaderView(CARD_HEADER);
        mediaCardFragment.updateContentView(mDescriptiveTextWithControlsView);

        onView(allOf(withId(R.id.card_view),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_view),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.primary_text), withText(AUDIO_VIEW_TITLE),
                isDescendantOfA(withId(R.id.media_layout)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.secondary_text), withText(AUDIO_VIEW_SUBTITLE),
                isDescendantOfA(withId(R.id.media_layout)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.optional_timer),
                isDescendantOfA(withId(R.id.media_layout)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_playback_controls_bar),
                isDescendantOfA(withId(R.id.media_layout)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.motion_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void showMediaCard_showsFullscreenMediaCardLayout_hidesDialerLayout() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        mActivity.runOnUiThread(fragment::showMediaCard);
        MediaCardFragment mediaCardFragment = (MediaCardFragment) fragment.getMediaFragment();

        onView(allOf(withId(R.id.motion_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_view),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.card_view),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void updateContentAndHeaderView_showsDialerControlBarControls_hidesMediaCardControls() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        mActivity.runOnUiThread(fragment::showInCallCard);
        DialerCardFragment dialerCardFragment = (DialerCardFragment) fragment.getInCallFragment();

        dialerCardFragment.updateHeaderView(CARD_HEADER);
        dialerCardFragment.updateContentView(mDescriptiveTextWithControlsViewWithButtons);

        onView(allOf(withId(R.id.optional_timer),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.button_left),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.button_center),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.button_right),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.bottom_card)),
                isDescendantOfA(withId(R.id.in_call_fragment_container))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.bottom_card)),
                isDescendantOfA(withId(R.id.media_fragment_container))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.bottom_card)),
                isDescendantOfA(withId(R.id.in_call_fragment_container))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.bottom_card)),
                isDescendantOfA(withId(R.id.media_fragment_container))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void updateContentAndHeaderView_audioContentWithControls_showsDialer_notMediaCard() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        mActivity.runOnUiThread(fragment::showInCallCard);
        DialerCardFragment dialerCardFragment = (DialerCardFragment) fragment.getInCallFragment();

        dialerCardFragment.updateHeaderView(CARD_HEADER);
        dialerCardFragment.updateContentView(mDescriptiveTextWithControlsViewWithButtons);

        onView(allOf(withId(R.id.optional_timer),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.button_left),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.button_center),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.button_right),
                isDescendantOfA(withId(R.id.descriptive_text_with_controls_layout)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_view),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_view),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(doesNotExist());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void mediaFragment_updateContentView_descriptiveText_hidesPlaybackControlsBar() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        MediaCardFragment mediaCardFragment = (MediaCardFragment) fragment.getMediaFragment();
        mediaCardFragment.updateContentView(mDescriptiveTextWithControlsView);
        mediaCardFragment.updateContentView(DESCRIPTIVE_TEXT_VIEW);

        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.descriptive_text_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void mediaFragment_updateContentView_textBlock_hidesPlaybackControlsBar() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        MediaCardFragment mediaCardFragment = (MediaCardFragment) fragment.getMediaFragment();
        mediaCardFragment.updateContentView(mDescriptiveTextWithControlsView);
        mediaCardFragment.updateContentView(TEXT_BLOCK_VIEW);

        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.text_block_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.media_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_CARD_FULLSCREEN)
    public void dialerFragment_updateContentView_descriptiveText_hidesDescriptiveControlsView() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        mActivity.runOnUiThread(fragment::showInCallCard);
        DialerCardFragment dialerCardFragment = (DialerCardFragment) fragment.getInCallFragment();
        dialerCardFragment.updateContentView(mDescriptiveTextWithControlsViewWithButtons);
        dialerCardFragment.updateContentView(DESCRIPTIVE_TEXT_VIEW);

        // card_background is displayed since the onRootLayoutChangeListener sets it visible
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void dialerFragment_updateContentView_textBlock_hidesDescriptiveControlsView() {
        AudioCardFragment fragment = (AudioCardFragment) mActivity.getSupportFragmentManager()
                .findFragmentById(R.id.bottom_card);
        mActivity.runOnUiThread(fragment::showInCallCard);
        DialerCardFragment dialerCardFragment = (DialerCardFragment) fragment.getInCallFragment();
        dialerCardFragment.updateContentView(mDescriptiveTextWithControlsViewWithButtons);
        dialerCardFragment.updateContentView(TEXT_BLOCK_VIEW);

        // card_background is displayed since the onRootLayoutChangeListener sets it visible
        onView(allOf(withId(R.id.card_background),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.card_background_image), is(instanceOf(CrossfadeImageView.class)),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.text_block_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.descriptive_text_with_controls_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
        onView(allOf(withId(R.id.media_layout),
                isDescendantOfA(withId(R.id.in_call_fragment_container)),
                isDescendantOfA(withId(R.id.bottom_card))))
                .inRoot(hasWindowLayoutParams())
                .check(matches(not(isDisplayed())));
    }
}
