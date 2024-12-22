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

import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.getFirstCustomActionInSet;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.skipBackStandardActions;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.skipForwardStandardActions;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updatePlayButtonWithPlaybackState;

import static java.lang.Integer.max;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.android.car.apps.common.RoundedDrawable;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.carlauncher.R;
import com.android.car.media.common.CustomPlaybackAction;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackController;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackStateWrapper;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.ui.PlaybackCardController;
import com.android.car.media.common.ui.PlaybackHistoryController;
import com.android.car.media.common.ui.PlaybackQueueController;

import java.util.ArrayList;
import java.util.List;

public class MediaCardController extends PlaybackCardController implements
        MediaCardPanelViewPagerAdapter.ViewPagerQueueCreator,
        MediaCardPanelViewPagerAdapter.ViewPagerHistoryCreator {

    private static final int SWIPE_MAX_OFF_PATH = 75;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private final MediaIntentRouter mMediaIntentRouter = MediaIntentRouter.getInstance();
    private Resources mViewResources;
    private View mPanelHandlebar;
    private LinearLayout mPanel;
    private MotionLayout mMotionLayout;
    private MediaCardFragment.MediaCardViewModel mCardViewModel;
    private ImageButton mSkipPrevButton;
    private ImageButton mSkipNextButton;
    private int mSkipPrevVisibility;
    private int mSkipNextVisibility;
    private int mAlbumCoverVisibility;
    private int mSubtitleVisibility;
    private int mLogoVisibility;

    private PlaybackQueueController mPlaybackQueueController;
    private PlaybackHistoryController mPlaybackHistoryController;

    private ViewPager2 mPager;
    private MediaCardPanelViewPagerAdapter mPagerAdapter;
    private Handler mHandler;

    @Override
    public void createQueueController(ViewGroup queueContainer) {
        mPlaybackQueueController = new PlaybackQueueController(
                queueContainer, /* queueResource */ Resources.ID_NULL,
                R.layout.media_card_queue_item, R.layout.media_card_queue_header_item,
                getViewLifecycleOwner(), mDataModel, mCardViewModel.getMediaItemsRepository(),
                /* uxrContentLimiter */ null, /* uxrConfigurationId */ 0);
        mPlaybackQueueController.setShowTimeForActiveQueueItem(false);
        mPlaybackQueueController.setShowIconForActiveQueueItem(false);
        mPlaybackQueueController.setShowThumbnailForQueueItem(true);
        mPlaybackQueueController.setShowSubtitleForQueueItem(true);
    }

    @Override
    public void createHistoryController(ViewGroup historyContainer) {
        mPlaybackHistoryController = new PlaybackHistoryController(getViewLifecycleOwner(),
                mCardViewModel, historyContainer, R.layout.media_card_history_item,
                R.layout.media_card_history_header_item, /* uxrConfigurationId */ 0);
        mPlaybackHistoryController.setupView();
    }

    /** Builder for {@link MediaCardController}. Overrides build() method to return
     * NowPlayingController rather than base {@link PlaybackCardController}
     */
    public static class Builder extends PlaybackCardController.Builder {

        @Override
        public MediaCardController build() {
            MediaCardController controller = new MediaCardController(this);
            controller.setupController();
            return controller;
        }
    }

    public MediaCardController(Builder builder) {
        super(builder);

        mCardViewModel = (MediaCardFragment.MediaCardViewModel) mViewModel;
        mViewResources = mView.getContext().getResources();

        mView.setOnClickListener(view -> {
            launchMediaAppOrClosePanel();
        });
        mView.findViewById(R.id.empty_panel).setOnClickListener(view -> {
            launchMediaAppOrClosePanel();
        });

        mPager = mView.findViewById(R.id.view_pager);
        mPagerAdapter = new MediaCardPanelViewPagerAdapter(mView.getContext());
        mPager.setAdapter(mPagerAdapter);
        mPagerAdapter.setQueueControllerProvider(this);
        mPagerAdapter.setHistoryControllerProvider(this);
        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (!mCardViewModel.getPanelExpanded()) {
                    return;
                }
                selectOverflow(position == getOverflowTabIndex());
                selectQueue(position == getQueueTabIndex());
                selectHistory(position == getHistoryTabIndex());
            }
        });

        mMotionLayout = mView.findViewById(R.id.motion_layout);

        mPanel = mView.findViewById(R.id.button_panel_background);
        mPanelHandlebar = mView.findViewById(R.id.media_card_panel_handlebar);

        mSkipPrevButton = mView.findViewById(R.id.playback_action_id1);
        mSkipNextButton = mView.findViewById(R.id.playback_action_id2);

        mMotionLayout.addTransitionListener(new MotionLayout.TransitionListener() {
            @Override
            public void onTransitionStarted(MotionLayout motionLayout, int i, int i1) {
            }

            @Override
            public void onTransitionChange(MotionLayout motionLayout, int i, int i1, float v) {
            }

            @Override
            public void onTransitionCompleted(MotionLayout motionLayout, int i) {
                if (mCardViewModel.getPanelExpanded()) {
                    mSkipPrevButton.setVisibility(View.GONE);
                    mSkipNextButton.setVisibility(View.GONE);
                    mLogo.setVisibility(View.GONE);
                }
            }

            @Override
            public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b, float v) {
            }
        });

        GestureDetector mCloseGestureDetector = new GestureDetector(mView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent event1, MotionEvent event2,
                            float velocityX, float velocityY) {
                        if (Math.abs(event1.getX() - event2.getX()) > SWIPE_MAX_OFF_PATH
                                || Math.abs(velocityY) < SWIPE_THRESHOLD_VELOCITY) {
                            // swipe was not vertical or was not fast enough
                            return false;
                        }
                        boolean isInClosingDirection = velocityY > 0;
                        if (isInClosingDirection) {
                            animateClosePanel();
                            return true;
                        }
                        return false;
                    }
                }
        );
        mPanelHandlebar.setOnClickListener((view) -> animateClosePanel());
        mPanelHandlebar.setOnTouchListener((view, event) ->
                mCloseGestureDetector.onTouchEvent(event));

        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void setupController() {
        super.setupController();

        mSkipPrevVisibility = mSkipPrevButton.getVisibility();
        mSkipNextVisibility = mSkipNextButton.getVisibility();
        mAlbumCoverVisibility = mAlbumCover.getVisibility();
        mSubtitleVisibility = mSubtitle.getVisibility();
        mLogoVisibility = mLogo.getVisibility();
    }

    @Override
    protected void updateMetadata(MediaItemMetadata metadata) {
        super.updateMetadata(metadata);
        if (mCardViewModel.getPanelExpanded()) {
            mSubtitleVisibility = mSubtitle.getVisibility();
            mSubtitle.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateAlbumCoverWithDrawable(Drawable drawable) {
        RoundedDrawable roundedDrawable = new RoundedDrawable(drawable, mView.getResources()
                .getFloat(R.dimen.media_card_album_art_drawable_corner_ratio));
        super.updateAlbumCoverWithDrawable(roundedDrawable);

        if (mCardViewModel.getPanelExpanded()) {
            mAlbumCoverVisibility = mAlbumCover.getVisibility();
            mAlbumCover.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void updateLogoWithDrawable(Drawable drawable) {
        super.updateLogoWithDrawable(drawable);
        if (mCardViewModel.getPanelExpanded()) {
            mLogoVisibility = mLogo.getVisibility();
            mLogo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateMediaSource(MediaSource mediaSource) {
        super.updateMediaSource(mediaSource);
        if (mCardViewModel.getPanelExpanded()) {
            mAppIcon.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void updateProgress(PlaybackProgress progress) {
        super.updateProgress(progress);
        ViewUtils.setVisible(mSeekBar, progress != null && progress.hasTime());
        if (progress == null || !progress.hasTime()) {
            mLogo.setVisibility(View.GONE);
        } else if (mDataModel.getMetadata().getValue() != null) {
            Uri logoUri = mLogo.prepareToDisplay(mDataModel.getMetadata().getValue());
            if (logoUri != null && !mCardViewModel.getPanelExpanded()) {
                mLogo.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void updatePlaybackState(PlaybackViewModel.PlaybackStateWrapper playbackState) {
        PlaybackController playbackController = mDataModel.getPlaybackController().getValue();
        if (playbackState != null) {
            updatePlayButtonWithPlaybackState(mPlayPauseButton, playbackState, playbackController);
            List<PlaybackViewModel.RawCustomPlaybackAction> usedCustomActions =
                    updateSkipButtonsAndReturnUsedStandardCustomActions(
                            playbackState, playbackController);

            boolean hasCustomActions = playbackState.getCustomActions().size() != 0;
            boolean isPreviouslyVisible = ViewUtils.isVisible(mActionOverflowButton);
            ViewUtils.setVisible(mActionOverflowButton, hasCustomActions);
            mPagerAdapter.setHasOverflow(hasCustomActions);
            if (mCardViewModel.getPanelExpanded() && isPreviouslyVisible != hasCustomActions) {
                animateClosePanel();
            }
            mPagerAdapter.notifyPlaybackStateChanged(playbackState,
                    playbackController, usedCustomActions);
        } else {
            mSkipPrevButton.setVisibility(View.GONE);
            mSkipNextButton.setVisibility(View.GONE);
        }

        if (mCardViewModel.getPanelExpanded()) {
            mSkipPrevVisibility = mSkipPrevButton.getVisibility();
            mSkipNextVisibility = mSkipNextButton.getVisibility();
            mSkipPrevButton.setVisibility(View.GONE);
            mSkipNextButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void setUpActionsOverflowButton() {
        super.setUpActionsOverflowButton();
        setOverflowState(mCardViewModel.getOverflowExpanded(), false);
    }

    @Override
    protected void handleCustomActionsOverflowButtonClicked(View overflow) {
        super.handleCustomActionsOverflowButtonClicked(overflow);
        setOverflowState(mCardViewModel.getOverflowExpanded(), true);
    }

    @Override
    protected void setUpQueueButton() {
        super.setUpQueueButton();
        setQueueState(mCardViewModel.getQueueVisible(), false);
    }

    @Override
    protected void updateQueueState(boolean hasQueue, boolean isQueueVisible) {
        super.updateQueueState(hasQueue, isQueueVisible);
        mPagerAdapter.setHasQueue(hasQueue);
        ViewUtils.setVisible(mQueueButton, hasQueue);
        if (mCardViewModel.getPanelExpanded() && !hasQueue) {
            animateClosePanel();
        }
    }

    @Override
    protected void handleQueueButtonClicked(View queue) {
        super.handleQueueButtonClicked(queue);
        setQueueState(mCardViewModel.getQueueVisible(), true);
    }

    @Override
    protected void setUpHistoryButton() {
        super.setUpHistoryButton();
        setHistoryState(mCardViewModel.getHistoryVisible(), false);
    }

    @Override
    protected void handleHistoryButtonClicked(View history) {
        super.handleHistoryButtonClicked(history);
        setHistoryState(mCardViewModel.getHistoryVisible(), true);
    }

    private void setOverflowState(boolean isExpanded, boolean stateSetThroughClick) {
        if (mActionOverflowButton == null) {
            return;
        }
        if (!mCardViewModel.getPanelExpanded()) {
            if (stateSetThroughClick) {
                saveViewVisibilityBeforeAnimation();
                mCardViewModel.setPanelExpanded(true);

                mPager.setCurrentItem(getOverflowTabIndex());

                mHandler.post(() -> mMotionLayout.transitionToEnd());

                selectOverflow(true);
            } else {
                unselectPanel();
            }
        } else {
            // If the panel is already open and overflow is clicked again,
            // always switch to overflow tab
            mPager.setCurrentItem(getOverflowTabIndex(), true);
            mPanel.setEnabled(true);

            selectOverflow(true);

            selectQueue(false);
            selectHistory(false);
        }
    }

    private void setQueueState(boolean isVisible, boolean stateSetThroughClick) {
        if (mQueueButton == null) {
            return;
        }
        if (!mCardViewModel.getPanelExpanded()) {
            if (stateSetThroughClick) {
                saveViewVisibilityBeforeAnimation();
                mCardViewModel.setPanelExpanded(true);
                mPager.setCurrentItem(getQueueTabIndex());

                mHandler.post(() -> mMotionLayout.transitionToEnd());

                selectQueue(true);
            } else {
                unselectPanel();
            }
        } else {
            // If the panel is already open and queue is clicked again,
            // always switch to queue tab
            mPager.setCurrentItem(getQueueTabIndex(), true);

            mPanel.setEnabled(true);

            selectQueue(true);

            selectOverflow(false);
            selectHistory(false);
        }
    }

    private void setHistoryState(boolean isVisible, boolean stateSetThroughClick) {
        if (mHistoryButton == null) {
            return;
        }
        int historyPos = getHistoryTabIndex();
        if (!mCardViewModel.getPanelExpanded()) {
            if (stateSetThroughClick) {
                saveViewVisibilityBeforeAnimation();
                mCardViewModel.setPanelExpanded(true);
                mPager.setCurrentItem(historyPos);

                mHandler.post(() -> mMotionLayout.transitionToEnd());

                selectHistory(true);
            } else {
                unselectPanel();
            }
        } else {
            // If the panel is already open and history is clicked again,
            // always switch to history tab
            mPager.setCurrentItem(historyPos, true);

            mPanel.setEnabled(true);

            selectHistory(true);

            selectOverflow(false);
            selectQueue(false);
        }
    }

    private void launchMediaAppOrClosePanel() {
        if (mCardViewModel.getPanelExpanded()) {
            animateClosePanel();
        } else {
            MediaSource mediaSource = mDataModel.getMediaSource().getValue();
            Intent intent = mediaSource != null ? mediaSource.getIntent() : null;
            mMediaIntentRouter.handleMediaIntent(intent);
        }
    }

    private void animateClosePanel() {
        mCardViewModel.setPanelExpanded(false);
        mMotionLayout.transitionToStart();
        restoreExtraViewsWhenPanelClosed();
        unselectAllPanelButtons();
    }

    private void unselectPanel() {
        mPanel.setEnabled(false);
        unselectAllPanelButtons();
    }

    private void selectQueue(boolean shouldSelect) {
        mCardViewModel.setQueueVisible(shouldSelect);
        mQueueButton.setSelected(shouldSelect);
    }

    private void selectOverflow(boolean shouldSelect) {
        mCardViewModel.setOverflowExpanded(shouldSelect);
        mActionOverflowButton.setSelected(shouldSelect);
    }

    private void selectHistory(boolean shouldSelect) {
        mCardViewModel.setHistoryVisible(shouldSelect);
        mHistoryButton.setSelected(shouldSelect);
    }

    private void unselectAllPanelButtons() {
        selectOverflow(false);
        selectQueue(false);
        selectHistory(false);
    }

    private void saveViewVisibilityBeforeAnimation() {
        mSubtitleVisibility = mSubtitle.getVisibility();
        mLogoVisibility = mLogo.getVisibility();
        mSkipPrevVisibility = mSkipPrevButton.getVisibility();
        mSkipNextVisibility = mSkipNextButton.getVisibility();
        mAlbumCoverVisibility = mAlbumCover.getVisibility();
    }

    private void restoreExtraViewsWhenPanelClosed() {
        mAlbumCover.setVisibility(mAlbumCoverVisibility);
        mAppIcon.setVisibility(View.VISIBLE);
        mSkipPrevButton.setVisibility(mSkipPrevVisibility);
        mSkipNextButton.setVisibility(mSkipNextVisibility);
        mSubtitle.setVisibility(mSubtitleVisibility);
        mLogo.setVisibility(mLogoVisibility);
    }

    /**
     * Set the mSkipNextButton and mSkipPrevButton with a skip action Drawable if sent by the
     * playbackState, otherwise with a skipForwardStandardAction and skipBackStandardAction
     * respectively. If none exist, hide the button.
     */
    private List<PlaybackViewModel.RawCustomPlaybackAction>
            updateSkipButtonsAndReturnUsedStandardCustomActions(PlaybackStateWrapper playbackState,
            PlaybackController playbackController) {
        List<PlaybackViewModel.RawCustomPlaybackAction> usedCustomActions =
                new ArrayList<PlaybackViewModel.RawCustomPlaybackAction>();
        updateSkipNextButtonWithSkipOrStandardAction(playbackState, playbackController,
                usedCustomActions);
        updateSkipPrevButtonWithSkipOrStandardAction(playbackState, playbackController,
                usedCustomActions);
        return usedCustomActions;
    }

    private void updateSkipNextButtonWithSkipOrStandardAction(
            PlaybackStateWrapper playbackState, PlaybackController playbackController,
            List<PlaybackViewModel.RawCustomPlaybackAction> usedCustomActions) {
        boolean isSkipNextEnabled = playbackState.isSkipNextEnabled();
        boolean isSkipNextReserved = playbackState.isSkipNextReserved();
        if ((isSkipNextEnabled || isSkipNextReserved)) {
            updateButton(mSkipNextButton, mView.getContext().getDrawable(
                    com.android.car.media.common.R.drawable.ic_skip_next),
                    mView.getContext().getDrawable(R.drawable.circle_button_background),
                    true, isSkipNextEnabled, (v) -> {
                if (playbackController != null) {
                    playbackController.skipToNext();
                }
            });
        } else {
            PlaybackViewModel.RawCustomPlaybackAction skipForwardCustomAction =
                    getFirstCustomActionInSet(playbackState.getCustomActions(),
                            skipForwardStandardActions);
            if (skipForwardCustomAction != null) {
                boolean isCustomActionUsed =
                        updateButtonWithCustomAction(mSkipNextButton, skipForwardCustomAction,
                                playbackController);
                if (isCustomActionUsed) {
                    usedCustomActions.add(skipForwardCustomAction);
                }
            } else {
                updateButton(mSkipNextButton, null, null, false, false, null);
            }
        }
    }

    private void updateSkipPrevButtonWithSkipOrStandardAction(
            PlaybackStateWrapper playbackState, PlaybackController playbackController,
            List<PlaybackViewModel.RawCustomPlaybackAction> usedCustomActions) {
        boolean isSkipPrevEnabled = playbackState.isSkipPreviousEnabled();
        boolean isSkipPrevReserved = playbackState.iSkipPreviousReserved();
        if ((isSkipPrevEnabled || isSkipPrevReserved)) {
            updateButton(mSkipPrevButton, mView.getContext().getDrawable(
                    com.android.car.media.common.R.drawable.ic_skip_previous),
                    mView.getContext().getDrawable(R.drawable.circle_button_background),
                    true, isSkipPrevEnabled, (v) -> {
                    if (playbackController != null) {
                        playbackController.skipToPrevious();
                    }
                });
        } else {
            PlaybackViewModel.RawCustomPlaybackAction skipBackCustomAction =
                    getFirstCustomActionInSet(playbackState.getCustomActions(),
                            skipBackStandardActions);
            if (skipBackCustomAction != null) {
                boolean isCustomActionUsed =
                        updateButtonWithCustomAction(mSkipPrevButton, skipBackCustomAction,
                                playbackController);
                if (isCustomActionUsed) {
                    usedCustomActions.add(skipBackCustomAction);
                }
            } else {
                updateButton(mSkipPrevButton, null, null, false, false, null);
            }
        }
    }

    private void updateButton(ImageButton button, Drawable imageDrawable,
            Drawable backgroundDrawable, boolean isVisible, boolean isEnabled,
            View.OnClickListener listener) {
        button.setImageDrawable(imageDrawable);
        button.setBackground(backgroundDrawable);
        ViewUtils.setVisible(button, isVisible);
        button.setEnabled(isEnabled);
        button.setOnClickListener(listener);
    }

    private boolean updateButtonWithCustomAction(ImageButton button,
            PlaybackViewModel.RawCustomPlaybackAction rawCustomAction,
            PlaybackController playbackController) {
        CustomPlaybackAction customAction = rawCustomAction
                .fetchDrawable(mView.getContext());
        if (customAction != null) {
            updateButton(button, customAction.mIcon, mView.getContext().getDrawable(
                    R.drawable.circle_button_background), true, true, (v) -> {
                if (playbackController != null) {
                        playbackController.doCustomAction(
                                customAction.mAction, customAction.mExtras);
                }
            });
            return true;
        } else {
            updateButton(button, null, null, false, false, null);
            return false;
        }
    }

    private int getOverflowTabIndex() {
        return hasOverflow() ? 0 : -1;
    }

    private int getQueueTabIndex() {
        if (!getMediaHasQueue()) return -1;
        return getOverflowTabIndex() + 1;
    }

    private int getHistoryTabIndex() {
        return max(getOverflowTabIndex(), getQueueTabIndex()) + 1;
    }

    private boolean hasOverflow() {
        PlaybackStateWrapper playbackState = mDataModel.getPlaybackStateWrapper().getValue();
        return playbackState != null && playbackState.getCustomActions().size() != 0;
    }
}
