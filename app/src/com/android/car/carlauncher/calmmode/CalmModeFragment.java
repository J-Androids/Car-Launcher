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

package com.android.car.carlauncher.calmmode;

import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.animation.Animator;
import androidx.core.animation.AnimatorInflater;
import androidx.core.animation.AnimatorSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.R;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackViewModel;

import java.util.ArrayList;
import java.util.List;

public final class CalmModeFragment extends Fragment {
    private static final String TAG = CalmModeFragment.class.getSimpleName();
    private static final boolean DEBUG = Build.isDebuggable();
    private View mContainerView;
    private TextView mMediaTitleView;
    private TextView mNavStateView;
    private TextView mTemperatureView;
    private TextView mClockView;
    private TextView mDateView;
    private ImageView mNavStateIconView;
    private TextView mTemperatureIconView;
    private ViewModelProvider mViewModelProvider;
    private TemperatureViewModel mTemperatureViewModel;
    private LiveData<TemperatureData> mTemperatureData;
    private PlaybackViewModel mPlaybackViewModel;
    @Nullable
    private NavigationStateViewModel mNavigationStateViewModel;
    private boolean mShowEntryAnimations;
    private final List<View> mViewsPendingAnimation = new ArrayList<>();
    private final AnimatorSet mAnimatorSet = new AnimatorSet();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (DEBUG) {
            Log.v(TAG, "onCreateView");
        }

        View rootView = inflater.inflate(R.layout.calm_mode_fragment, container, false);
        mViewModelProvider = new ViewModelProvider(getViewModelStore(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()));

        mContainerView = rootView.findViewById(R.id.calm_mode_container);
        mClockView = rootView.findViewById(R.id.clock);
        mDateView = rootView.findViewById(R.id.date);
        mNavStateView = rootView.findViewById(R.id.nav_state);
        mNavStateIconView = rootView.findViewById(R.id.nav_state_icon);
        mTemperatureIconView = rootView.findViewById(R.id.temperature_icon);
        mTemperatureView = rootView.findViewById(R.id.temperature);
        mMediaTitleView = rootView.findViewById(R.id.media_title);
        mShowEntryAnimations = true;

        hideStatusBar();
        initExitOnClick();
        initDate();
        initNavState();
        initTemperature();
        initMediaTitle();
        initClockAndPlayEntryAnimations();

        return rootView;
    }

    private void hideStatusBar() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(
                () -> {
                    if (getActivity() == null) return;
                    getActivity().getWindow().getDecorView().getWindowInsetsController()
                            .hide(WindowInsets.Type.statusBars());
                },
                getResources().getInteger(R.integer.calm_mode_activity_fade_duration));
    }

    private void initMediaTitle() {
        if (DEBUG) {
            Log.v(TAG, "initMediaTitle()");
        }
        if (shouldShowMedia()) {
            mPlaybackViewModel = PlaybackViewModel.get(requireActivity().getApplication(),
                    MEDIA_SOURCE_MODE_PLAYBACK);
            mPlaybackViewModel.getMetadata().observe(this, this::updateMediaTitle);
        }
    }

    private void initTemperature() {
        if (DEBUG) {
            Log.v(TAG, "initTemperature()");
        }
        if (shouldShowTemperature()) {
            mTemperatureViewModel = mViewModelProvider.get(TemperatureViewModel.class);
            mTemperatureData = mTemperatureViewModel.getTemperatureData();
            mTemperatureData.observe(getViewLifecycleOwner(), this::updateTemperatureData);
        }
    }

    private void initDate() {
        if (DEBUG) {
            Log.v(TAG, "initDate()");
        }
        if (!shouldShowDate()) {
            return;
        }
        if (mShowEntryAnimations) {
            mDateView.setVisibility(View.INVISIBLE);
            mViewsPendingAnimation.add(mDateView);
        } else {
            mDateView.setVisibility(View.VISIBLE);
        }

    }

    private void playEntryAnimations() {
        mShowEntryAnimations = false;
        List<Animator> animList = new ArrayList<>();

        for (View view : mViewsPendingAnimation) {
            if (getContext() == null) {
                return;
            }
            Animator animator = AnimatorInflater.loadAnimator(getContext(),
                    R.animator.calm_mode_enter);
            animator.setTarget(view);
            Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                    view.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {

                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {

                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {

                }
            };
            animator.addListener(animatorListener);
            animList.add(animator);
        }
        mViewsPendingAnimation.clear();
        mAnimatorSet.setDuration(getResources().getInteger(
                R.integer.calm_mode_content_fade_duration));
        mAnimatorSet.playTogether(animList);
        mAnimatorSet.start();
    }
    private void initClockAndPlayEntryAnimations() {
        if (DEBUG) {
            Log.v(TAG, "initClock()");
        }
        if (shouldShowClock()) {
            mClockView.setVisibility(View.INVISIBLE);
            if (getContext() == null) {
                return;
            }
            Animator animator = AnimatorInflater.loadAnimator(getContext(),
                    R.animator.calm_mode_enter);
            animator.setStartDelay(getResources().getInteger(
                    R.integer.calm_mode_activity_fade_duration));
            animator.setTarget(mClockView);

            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                    mClockView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    playEntryAnimations();
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {

                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {

                }
            });
            animator.start();
        }
    }

    private void initExitOnClick() {
        if (DEBUG) {
            Log.v(TAG, "initExitOnTouch()");
        }
        mContainerView.setOnClickListener((view) -> {
            if (DEBUG) {
                Log.v(TAG, "Detected touch, exiting Calm mode");
            }
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    private void initNavState() {
        if (DEBUG) {
            Log.v(TAG, "initNavState()");
        }

        if (shouldShowNavigation()) {
            mNavigationStateViewModel = mViewModelProvider.get(NavigationStateViewModel.class);
            mNavigationStateViewModel
                    .getNavigationState()
                    .observe(this, this::updateNavigationState);
        }
    }

    private boolean shouldShowMedia() {
        return getResources().getBoolean(R.bool.config_calmMode_showMedia);
    }

    private boolean shouldShowTemperature() {
        return getResources().getBoolean(R.bool.config_calmMode_showTemperature);
    }

    private boolean shouldShowClock() {
        return getResources().getBoolean(R.bool.config_calmMode_showClock);
    }

    private boolean shouldShowDate() {
        return getResources().getBoolean(R.bool.config_calmMode_showDate);
    }

    private boolean shouldShowNavigation() {
        return getResources().getBoolean(R.bool.config_calmMode_showNavigation);
    }

    private void updateTemperatureData(@Nullable TemperatureData temperatureData) {
        if (temperatureData == null) {
            mTemperatureView.setVisibility(View.GONE);
            mTemperatureIconView.setVisibility(View.GONE);
            mTemperatureView.setText("");
            return;
        }
        if (mShowEntryAnimations) {
            mTemperatureView.setVisibility(View.INVISIBLE);
            mTemperatureIconView.setVisibility(View.INVISIBLE);
            mViewsPendingAnimation.add(mTemperatureView);
            mViewsPendingAnimation.add(mTemperatureIconView);
        } else {
            mTemperatureView.setVisibility(View.VISIBLE);
            mTemperatureIconView.setVisibility(View.VISIBLE);
        }
        mTemperatureView.setText(
                TemperatureData.buildTemperatureString(
                        temperatureData, getResources().getConfiguration().getLocales().get(0),
                        /* showUnit = */ false));
    }

    private void updateNavigationState(NavigationStateData navState) {
        if (DEBUG) {
            Log.v(TAG, "updateNavigationState navState = " + navState);
        }

        if (navState == null) {
            mNavStateView.setVisibility(View.GONE);
            mNavStateIconView.setVisibility(View.GONE);
            mNavStateView.setText("");
            return;
        }

        if (mShowEntryAnimations) {
            mNavStateView.setVisibility(View.INVISIBLE);
            mNavStateIconView.setVisibility(View.INVISIBLE);
            mViewsPendingAnimation.add(mNavStateView);
            mViewsPendingAnimation.add(mNavStateIconView);
        } else {
            mNavStateView.setVisibility(View.VISIBLE);
            mNavStateIconView.setVisibility(View.VISIBLE);
        }
        mNavStateView.setText(
                NavigationStateData.buildTripStatusString(navState,
                        getResources().getConfiguration().getLocales().get(0),
                        getResources().getString(R.string.calm_mode_separator)));
    }

    @VisibleForTesting
    void updateMediaTitle(MediaItemMetadata mediaItemMetadata) {
        if (DEBUG) {
            Log.v(TAG, "updateMediaTitle mediaItemMetadata = " + mediaItemMetadata);
        }
        if (mediaItemMetadata == null || mediaItemMetadata.getTitle() == null
                || mediaItemMetadata.getTitle().length() == 0) {
            mMediaTitleView.setVisibility(View.GONE);
            mClockView.setTranslationY(0f);
            mMediaTitleView.setText("");
            return;
        }

        StringBuilder medaTitleBuilder = new StringBuilder();
        medaTitleBuilder.append(mediaItemMetadata.getTitle());

        if (mediaItemMetadata.getSubtitle() != null
                && mediaItemMetadata.getSubtitle().length() > 0) {
            medaTitleBuilder.append(getResources().getString(R.string.calm_mode_separator));
            medaTitleBuilder.append(mediaItemMetadata.getSubtitle());
        }

        mClockView.setTranslationY(
                -getResources().getDimension(R.dimen.calm_mode_clock_translationY));
        mContainerView.requestLayout();
        if (mShowEntryAnimations) {
            mMediaTitleView.setVisibility(View.INVISIBLE);
            mViewsPendingAnimation.add(mMediaTitleView);
        } else {
            mMediaTitleView.setVisibility(View.VISIBLE);
        }
        mMediaTitleView.setText(medaTitleBuilder.toString());
    }

    @Override
    public void onStart() {
        super.onStart();
        int launchType = requireActivity().getIntent().getIntExtra(
                CalmModeStatsLogHelper.INTENT_EXTRA_CALM_MODE_LAUNCH_TYPE,
                CalmModeStatsLogHelper.CalmModeLaunchType.UNSPECIFIED_LAUNCH_TYPE);
        CalmModeStatsLogHelper.getInstance().logSessionStarted(launchType);
    }

    @Override
    public void onStop() {
        super.onStop();
        CalmModeStatsLogHelper.getInstance().logSessionFinished();
    }

}
