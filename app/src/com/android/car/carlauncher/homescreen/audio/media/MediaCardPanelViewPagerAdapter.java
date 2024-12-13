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

import static com.android.car.carlauncher.homescreen.audio.media.MediaCardPanelViewPagerAdapter.Tab.HistoryTab;
import static com.android.car.carlauncher.homescreen.audio.media.MediaCardPanelViewPagerAdapter.Tab.OverflowTab;
import static com.android.car.carlauncher.homescreen.audio.media.MediaCardPanelViewPagerAdapter.Tab.QueueTab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TableLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.util.ViewUtils;
import com.android.car.carlauncher.R;
import com.android.car.media.common.CustomPlaybackAction;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackController;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackStateWrapper;

import java.util.ArrayList;
import java.util.List;

public class MediaCardPanelViewPagerAdapter extends
        RecyclerView.Adapter<MediaCardPanelViewPagerAdapter.PanelViewHolder> {

    private final Context mContext;
    private boolean mHasQueue;
    private ViewPagerQueueCreator mQueueCreator;
    private ViewPagerHistoryCreator mHistoryCreator;

    private boolean mHasOverflow;
    private PlaybackStateWrapper mPlaybackState;
    private PlaybackController mPlaybackController;
    private List<PlaybackViewModel.RawCustomPlaybackAction> mCustomActionsToExclude =
            new ArrayList<PlaybackViewModel.RawCustomPlaybackAction>();

    enum Tab { OverflowTab, QueueTab, HistoryTab };

    public MediaCardPanelViewPagerAdapter(Context context) {
        this.mContext = context;
    }

    @NonNull
    @Override
    public PanelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PanelViewHolder(LayoutInflater.from(mContext).inflate(
                R.layout.media_card_panel_content_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PanelViewHolder holder, int position) {
        TableLayout overflowGrid = holder.itemView.findViewById(R.id.overflow_grid);
        FrameLayout queue = holder.itemView.findViewById(R.id.queue_list_container);
        FrameLayout history = holder.itemView.findViewById(R.id.history_list_container);

        Tab tab = getTab(position);
        switch (tab) {
            case OverflowTab: {
                updateCustomActionsWithPlaybackState(holder.itemView);
                break;
            }
            case QueueTab: {
                mQueueCreator.createQueueController(queue);
                break;
            }
            case HistoryTab: {
                mHistoryCreator.createHistoryController(history);
                break;
            }
        }
        overflowGrid.setVisibility(tab == OverflowTab ? View.VISIBLE : View.GONE);
        queue.setVisibility(tab == QueueTab ? View.VISIBLE : View.GONE);
        history.setVisibility(tab == HistoryTab ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return mHasQueue && mHasOverflow ? 3 : (!mHasQueue && !mHasOverflow ? 1 : 2);
    }

    /** Notify ViewHolder to rebind when a media source queue status changes */
    public void setHasQueue(boolean hasQueue) {
        mHasQueue = hasQueue;
        notifyDataSetChanged();
    }

    public void setQueueControllerProvider(ViewPagerQueueCreator queueCreator) {
        mQueueCreator = queueCreator;
    }

    public void setHistoryControllerProvider(ViewPagerHistoryCreator historyCreator) {
        mHistoryCreator = historyCreator;
    }

    /** Notify ViewHolder to rebind when a media source overflow status changes */
    public void setHasOverflow(boolean hasOverflow) {
        if (mHasOverflow != hasOverflow) {
            mHasOverflow = hasOverflow;
            notifyDataSetChanged();
        }
    }

    /** Notify a change in playback state so ViewHolder binds with latest update */
    public void notifyPlaybackStateChanged(PlaybackStateWrapper playbackState,
            PlaybackController playbackController,
            List<PlaybackViewModel.RawCustomPlaybackAction> customActionsToExclude) {
        mPlaybackState = playbackState;
        mPlaybackController = playbackController;
        mCustomActionsToExclude.clear();
        mCustomActionsToExclude.addAll(customActionsToExclude);
        if (mHasOverflow) {
            notifyItemChanged(0);
        }
    }

    private void updateCustomActionsWithPlaybackState(View itemView) {
        List<ImageButton> actions = ViewUtils.getViewsById(itemView,
                mContext.getResources(), R.array.playback_action_slot_ids, null);
        Drawable defaultDrawable = mContext.getDrawable(
                R.drawable.empty_action_drawable);
        List<PlaybackViewModel.RawCustomPlaybackAction> customActions = mPlaybackState == null
                ? new ArrayList<PlaybackViewModel.RawCustomPlaybackAction>()
                : mPlaybackState.getCustomActions();
        customActions.removeAll(mCustomActionsToExclude);
        List<ImageButton> actionsToFill = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            ImageButton button = actions.get(i);
            if (button != null) {
                actionsToFill.add(button);
                button.setBackground(null);
                button.setImageDrawable(defaultDrawable);
                button.setImageTintList(ColorStateList.valueOf(
                        mContext.getResources().getColor(
                                R.color.car_surface_variant, /* theme */ null)));
                ViewUtils.setVisible(button, true);
            }
        }

        int i = 0;
        for (PlaybackViewModel.RawCustomPlaybackAction a : customActions) {
            if (i < actionsToFill.size()) {
                CustomPlaybackAction customAction = a.fetchDrawable(mContext);
                if (customAction != null) {
                    actionsToFill.get(i).setImageDrawable(customAction.mIcon);
                    actionsToFill.get(i).setBackgroundColor(Color.TRANSPARENT);
                    actionsToFill.get(i).setImageTintList(ColorStateList.valueOf(
                            mContext.getResources().getColor(
                                    R.color.car_on_surface, /* theme */ null)));
                    ViewUtils.setVisible(actionsToFill.get(i), true);
                    actionsToFill.get(i).setOnClickListener(v -> {
                        if (mPlaybackController != null) {
                            mPlaybackController.doCustomAction(
                                    customAction.mAction, customAction.mExtras);
                        }
                    });
                }
                i++;
            } else {
                break;
            }
        }
    }

    private Tab getTab(int index) {
        if (index == getQueueTabIndex()) {
            return QueueTab;
        } else if (index == getOverflowTabIndex()) {
            return OverflowTab;
        } else {
            return HistoryTab;
        }
    }

    private int getQueueTabIndex() {
        if (!mHasQueue) return -1;
        return getOverflowTabIndex() + 1;
    }

    private int getOverflowTabIndex() {
        return mHasOverflow ? 0 : -1;
    }

    static class PanelViewHolder extends RecyclerView.ViewHolder {

        PanelViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    interface ViewPagerQueueCreator {
        void createQueueController(ViewGroup queueContainer);
    }

    interface ViewPagerHistoryCreator {
        void createHistoryController(ViewGroup historyContainer);
    }
}
