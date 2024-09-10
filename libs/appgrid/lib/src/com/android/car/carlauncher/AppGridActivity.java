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

import static androidx.lifecycle.FlowLiveDataConversions.asLiveData;

import static com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection;
import static com.android.car.carlauncher.AppGridConstants.PageOrientation;
import static com.android.car.hidden.apis.HiddenApiAccess.getDragSurface;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.ValueAnimator;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.media.CarMediaManager;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.DragEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.datasources.AppOrderDataSource;
import com.android.car.carlauncher.datasources.AppOrderProtoDataSourceImpl;
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSource;
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSourceImpl;
import com.android.car.carlauncher.datasources.LauncherActivitiesDataSource;
import com.android.car.carlauncher.datasources.LauncherActivitiesDataSourceImpl;
import com.android.car.carlauncher.datasources.MediaTemplateAppsDataSource;
import com.android.car.carlauncher.datasources.MediaTemplateAppsDataSourceImpl;
import com.android.car.carlauncher.datasources.UXRestrictionDataSource;
import com.android.car.carlauncher.datasources.UXRestrictionDataSourceImpl;
import com.android.car.carlauncher.datasources.restricted.DisabledAppsDataSource;
import com.android.car.carlauncher.datasources.restricted.DisabledAppsDataSourceImpl;
import com.android.car.carlauncher.datasources.restricted.TosDataSource;
import com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl;
import com.android.car.carlauncher.datastore.launcheritem.LauncherItemListSource;
import com.android.car.carlauncher.pagination.PageMeasurementHelper;
import com.android.car.carlauncher.pagination.PaginationController;
import com.android.car.carlauncher.recyclerview.AppGridAdapter;
import com.android.car.carlauncher.recyclerview.AppGridItemAnimator;
import com.android.car.carlauncher.recyclerview.AppGridLayoutManager;
import com.android.car.carlauncher.recyclerview.AppItemViewHolder;
import com.android.car.carlauncher.repositories.AppGridRepository;
import com.android.car.carlauncher.repositories.AppGridRepositoryImpl;
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory;
import com.android.car.carlauncher.repositories.appactions.AppShortcutsFactory;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;

import java.lang.annotation.Retention;
import java.util.Collections;

import kotlin.Unit;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;

/**
 * Launcher activity that shows a grid of apps.
 */
public class AppGridActivity extends AppCompatActivity implements
        AppGridPageSnapper.PageSnapListener, AppItemViewHolder.AppItemDragListener,
        PaginationController.DimensionUpdateListener,
        AppGridAdapter.AppGridAdapterListener {
    private static final String TAG = "AppGridActivity";
    private static final boolean DEBUG_BUILD = false;
    private static final String MODE_INTENT_EXTRA = "com.android.car.carlauncher.mode";
    private static CarUiShortcutsPopup sCarUiShortcutsPopup;

    private boolean mShowAllApps = true;
    private boolean mShowToolbar = true;
    private Car mCar;
    private Mode mMode;
    private AppGridAdapter mAdapter;
    private AppGridRecyclerView mRecyclerView;
    private PageIndicator mPageIndicator;
    private AppGridLayoutManager mLayoutManager;
    private boolean mIsCurrentlyDragging;
    private long mOffPageHoverBeforeScrollMs;
    private Banner mBanner;

    private AppGridDragController mAppGridDragController;
    private PaginationController mPaginationController;

    private int mNumOfRows;
    private int mNumOfCols;
    private int mAppGridMarginHorizontal;
    private int mAppGridMarginVertical;
    private int mAppGridWidth;
    private int mAppGridHeight;
    @PageOrientation
    private int mPageOrientation;

    private int mCurrentScrollOffset;
    private int mCurrentScrollState;
    private int mNextScrollDestination;
    private AppGridPageSnapper.AppGridPageSnapCallback mSnapCallback;
    private AppItemViewHolder.AppItemDragCallback mDragCallback;
    private BackgroundAnimationHelper mBackgroundAnimationHelper;

    private AppGridViewModel mAppGridViewModel;

    @Retention(SOURCE)
    @IntDef({APP_TYPE_LAUNCHABLES, APP_TYPE_MEDIA_SERVICES})
    @interface AppTypes {}
    static final int APP_TYPE_LAUNCHABLES = 1;
    static final int APP_TYPE_MEDIA_SERVICES = 2;

    public enum Mode {
        ALL_APPS(R.string.app_launcher_title_all_apps,
                APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                true),
        MEDIA_ONLY(R.string.app_launcher_title_media_only,
                APP_TYPE_MEDIA_SERVICES,
                true),
        MEDIA_POPUP(R.string.app_launcher_title_media_only,
                APP_TYPE_MEDIA_SERVICES,
                false),
        ;
        @StringRes
        public final int mTitleStringId;
        @AppTypes
        public final int mAppTypes;
        public final boolean mOpenMediaCenter;

        Mode(@StringRes int titleStringId, @AppTypes int appTypes,
                boolean openMediaCenter) {
            mTitleStringId = titleStringId;
            mAppTypes = appTypes;
            mOpenMediaCenter = openMediaCenter;
        }
    }

    /**
     * Updates the state of the app grid components depending on the driving state.
     */
    private void handleDistractionOptimization(boolean requiresDistractionOptimization) {
        mAdapter.setIsDistractionOptimizationRequired(requiresDistractionOptimization);
        if (requiresDistractionOptimization) {
            // if the user start driving while drag is in action, we cancel existing drag operations
            if (mIsCurrentlyDragging) {
                mIsCurrentlyDragging = false;
                mLayoutManager.setShouldLayoutChildren(true);
                mRecyclerView.cancelDragAndDrop();
            }
            dismissShortcutPopup();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // TODO (b/267548246) deprecate toolbar and find another way to hide debug apps
        mShowToolbar = false;
        if (mShowToolbar) {
            setTheme(R.style.Theme_Launcher_AppGridActivity);
        } else {
            setTheme(R.style.Theme_Launcher_AppGridActivity_NoToolbar);
        }
        super.onCreate(savedInstanceState);

        mCar = Car.createCar(this);
        setContentView(R.layout.app_grid_activity);
        updateMode();
        initViewModel();

        if (mShowToolbar) {
            ToolbarController toolbar = CarUi.requireToolbar(this);

            toolbar.setNavButtonMode(NavButtonMode.CLOSE);

            if (DEBUG_BUILD) {
                toolbar.setMenuItems(Collections.singletonList(MenuItem.builder(this)
                        .setDisplayBehavior(MenuItem.DisplayBehavior.NEVER)
                        .setTitle(R.string.hide_debug_apps)
                        .setOnClickListener(i -> {
                            mShowAllApps = !mShowAllApps;
                            i.setTitle(mShowAllApps
                                    ? R.string.hide_debug_apps
                                    : R.string.show_debug_apps);
                        })
                        .build()));
            }
        }

        mSnapCallback = new AppGridPageSnapper.AppGridPageSnapCallback(this);
        mDragCallback = new AppItemViewHolder.AppItemDragCallback(this);

        mNumOfCols = getResources().getInteger(R.integer.car_app_selector_column_number);
        mNumOfRows = getResources().getInteger(R.integer.car_app_selector_row_number);
        mAppGridDragController = new AppGridDragController();
        mOffPageHoverBeforeScrollMs = getResources().getInteger(
                R.integer.ms_off_page_hover_before_scroll);

        mPageOrientation = getResources().getBoolean(R.bool.use_vertical_app_grid)
                ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL;

        mRecyclerView = requireViewById(R.id.apps_grid);
        mRecyclerView.setFocusable(false);
        mLayoutManager = new AppGridLayoutManager(this, mNumOfCols, mNumOfRows, mPageOrientation);
        mRecyclerView.setLayoutManager(mLayoutManager);

        AppGridPageSnapper pageSnapper = new AppGridPageSnapper(
                this,
                mNumOfCols,
                mNumOfRows,
                mSnapCallback);
        pageSnapper.attachToRecyclerView(mRecyclerView);

        mRecyclerView.setItemAnimator(new AppGridItemAnimator());

        // hide the default scrollbar and replace it with a visual page indicator
        mRecyclerView.setVerticalScrollBarEnabled(false);
        mRecyclerView.setHorizontalScrollBarEnabled(false);
        mRecyclerView.addOnScrollListener(new AppGridOnScrollListener());

        // TODO: (b/271637411) move this to be contained in a scroll controller
        mPageIndicator = requireViewById(R.id.page_indicator);
        FrameLayout pageIndicatorContainer = requireViewById(R.id.page_indicator_container);
        mPageIndicator.setContainer(pageIndicatorContainer);

        // recycler view is set to LTR to prevent layout manager from reassigning layout direction.
        // instead, PageIndexinghelper will determine the grid index based on the system layout
        // direction and provide LTR mapping at adapter level.
        mRecyclerView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pageIndicatorContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        // we create but do not attach the adapter to recyclerview until view tree layout is
        // complete and the total size of the app grid is measureable.
        mAdapter = new AppGridAdapter(this, mNumOfCols, mNumOfRows,
                /* dragCallback */ mDragCallback,
                /* snapCallback */ mSnapCallback, this, mMode);

        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                // scroll state will need to be updated after item has been dropped
                mNextScrollDestination = mSnapCallback.getSnapPosition();
                updateScrollState();
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        asLiveData(mAppGridViewModel.getAppList()).observe(this,
                appItems -> {
                    mAdapter.setLauncherItems(appItems);
                    mNextScrollDestination = mSnapCallback.getSnapPosition();
                    updateScrollState();
                });

        asLiveData(mAppGridViewModel.requiresDistractionOptimization()).observe(this,
                uxRestrictions -> {
                    handleDistractionOptimization(uxRestrictions);
                });

        // set drag listener and global layout listener, which will dynamically adjust app grid
        // height and width depending on device screen size.
        if (getResources().getBoolean(R.bool.config_allow_reordering)) {
            mRecyclerView.setOnDragListener(new AppGridDragListener());
        }

        // since some measurements for window size may not be available yet during onCreate or may
        // later change, we add a listener that redraws the app grid when window size changes.
        LinearLayout windowBackground = requireViewById(R.id.apps_grid_background);
        windowBackground.setOrientation(
                isHorizontal() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        PaginationController.DimensionUpdateCallback dimensionUpdateCallback =
                new PaginationController.DimensionUpdateCallback();
        dimensionUpdateCallback.addListener(mRecyclerView);
        dimensionUpdateCallback.addListener(mPageIndicator);
        dimensionUpdateCallback.addListener(this);
        mPaginationController = new PaginationController(windowBackground, dimensionUpdateCallback);

        mBanner = requireViewById(R.id.tos_banner);

        mBackgroundAnimationHelper = new BackgroundAnimationHelper(windowBackground, mBanner);

        setupTosBanner();
    }

    private void initViewModel() {
        LauncherActivitiesDataSource launcherActivities = new LauncherActivitiesDataSourceImpl(
                getSystemService(LauncherApps.class),
                (broadcastReceiver, intentFilter) -> {
                    registerReceiver(broadcastReceiver, intentFilter);
                    return Unit.INSTANCE;
                }, broadcastReceiver -> {
            unregisterReceiver(broadcastReceiver);
            return Unit.INSTANCE;
        },
                android.os.Process.myUserHandle(),
                getApplication().getResources(),
                Dispatchers.getDefault()
        );
        MediaTemplateAppsDataSource mediaTemplateApps = new MediaTemplateAppsDataSourceImpl(
                getPackageManager(),
                getApplication(),
                Dispatchers.getDefault()
        );

        DisabledAppsDataSource disabledApps = new DisabledAppsDataSourceImpl(getContentResolver(),
                getPackageManager(), Dispatchers.getIO());
        TosDataSource tosApps = new TosDataSourceImpl(getContentResolver(), getPackageManager(),
                Dispatchers.getIO());
        ControlCenterMirroringDataSource controlCenterMirroringDataSource =
                new ControlCenterMirroringDataSourceImpl(getApplication().getResources(),
                        (intent, serviceConnection, flags) -> {
                            bindService(intent, serviceConnection, flags);
                            return Unit.INSTANCE;
                        },
                        (serviceConnection) -> {
                            unbindService(serviceConnection);
                            return Unit.INSTANCE;
                        },
                        getPackageManager(),
                        Dispatchers.getIO()
                );
        UXRestrictionDataSource uxRestrictionDataSource = new UXRestrictionDataSourceImpl(
                (CarUxRestrictionsManager) mCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE),
                (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE),
                getSystemService(MediaSessionManager.class),
                getApplication().getResources(),
                Dispatchers.getDefault()
        );
        AppOrderDataSource appOrderDataSource = new AppOrderProtoDataSourceImpl(
                new LauncherItemListSource(getFilesDir(), "order.data"),
                Dispatchers.getIO()
        );

        PackageManager packageManager = getPackageManager();
        AppLaunchProviderFactory launchProviderFactory = new AppLaunchProviderFactory(
                (CarMediaManager) mCar.getCarManager(Car.CAR_MEDIA_SERVICE),
                mMode.mOpenMediaCenter,
                () -> {
                    finish();
                    return Unit.INSTANCE;
                },
                getPackageManager());
        AppShortcutsFactory appShortcutsFactory = new AppShortcutsFactory(
                (CarMediaManager) mCar.getCarManager(Car.CAR_MEDIA_SERVICE),
                Collections.emptySet(),
                this::onShortcutsShow
        );
        CoroutineDispatcher bgDispatcher = Dispatchers.getDefault();

        AppGridRepository repo = new AppGridRepositoryImpl(launcherActivities, mediaTemplateApps,
                disabledApps, tosApps, controlCenterMirroringDataSource, uxRestrictionDataSource,
                appOrderDataSource, packageManager, launchProviderFactory, appShortcutsFactory,
                bgDispatcher);

        mAppGridViewModel = new ViewModelProvider(this,
                AppGridViewModel.Companion.provideFactory(repo, getApplication(), this, null)).get(
                AppGridViewModel.class);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateMode();
    }

    @Override
    protected void onDestroy() {
        if (mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        super.onDestroy();
    }

    private void updateMode() {
        mMode = parseMode(getIntent());
        setTitle(mMode.mTitleStringId);
        if (mShowToolbar) {
            CarUi.requireToolbar(this).setTitle(mMode.mTitleStringId);
        }
    }

    @VisibleForTesting
    boolean isHorizontal() {
        return AppGridConstants.isHorizontal(mPageOrientation);
    }

    /**
     * Note: This activity is exported, meaning that it might receive intents from any source.
     * Intent data parsing must be extra careful.
     */
    @NonNull
    private Mode parseMode(@Nullable Intent intent) {
        String mode = intent != null ? intent.getStringExtra(MODE_INTENT_EXTRA) : null;
        try {
            return mode != null ? Mode.valueOf(mode) : Mode.ALL_APPS;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Received invalid mode: " + mode, e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScrollState();
        mAdapter.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        mAppGridViewModel.updateMode(mMode);
    }

    @Override
    public void onDimensionsUpdated(PageMeasurementHelper.PageDimensions pageDimens,
            PageMeasurementHelper.GridDimensions gridDimens) {
        // TODO(b/271637411): move this method into a scroll controller
        mAppGridMarginHorizontal = pageDimens.marginHorizontalPx;
        mAppGridMarginVertical = pageDimens.marginVerticalPx;
        mAppGridWidth = gridDimens.gridWidthPx;
        mAppGridHeight = gridDimens.gridHeightPx;
    }

    /**
     * Updates the scroll state after receiving data changes, such as new apps being added or
     * reordered, and when user returns to launcher onResume.
     *
     * Additionally, notify page indicator to handle resizing in case new app addition creates a
     * new page or deleted a page.
     */
    void updateScrollState() {
        // TODO(b/271637411): move this method into a scroll controller
        // to calculate how many pages we need to offset, we use the scroll offset anchor position
        // as item count and map to the page which the anchor is on.
        int offsetPageCount = mAdapter.getPageCount(mNextScrollDestination + 1) - 1;
        mRecyclerView.suppressLayout(false);
        mCurrentScrollOffset = offsetPageCount * (isHorizontal()
                ? (mAppGridWidth + 2 * mAppGridMarginHorizontal)
                : (mAppGridHeight + 2 * mAppGridMarginVertical));
        mLayoutManager.scrollToPositionWithOffset(/* position */
                offsetPageCount * mNumOfRows * mNumOfCols, /* offset */ 0);

        mPageIndicator.updateOffset(mCurrentScrollOffset);
        mPageIndicator.updatePageCount(mAdapter.getPageCount());
    }

    @Override
    protected void onPause() {
        dismissShortcutPopup();
        super.onPause();
    }

    @Override
    public void onSnapToPosition(int position) {
        mNextScrollDestination = position;
    }

    @Override
    public void onItemLongPressed(boolean isLongPressed) {
        // after the user long presses the app icon, scrolling should be disabled until long press
        // is canceled as to allow MotionEvent to be interpreted as attempt to drag the app icon.
        mRecyclerView.suppressLayout(isLongPressed);
    }

    @Override
    public void onItemSelected(int gridPositionFrom) {
        mIsCurrentlyDragging = true;
        mLayoutManager.setShouldLayoutChildren(false);
        mAdapter.setDragStartPoint(gridPositionFrom);
        dismissShortcutPopup();
    }

    @Override
    public void onItemDragged() {
        mAppGridDragController.cancelDelayedPageFling();
    }

    @Override
    public void onDragExited(int gridPosition, @AppItemBoundDirection int exitDirection) {
        if (mAdapter.getOffsetBoundDirection(gridPosition) == exitDirection) {
            mAppGridDragController.postDelayedPageFling(exitDirection);
        }
    }

    @Override
    public void onItemDropped(int gridPositionFrom, int gridPositionTo) {
        mLayoutManager.setShouldLayoutChildren(true);
        mAdapter.moveAppItem(gridPositionFrom, gridPositionTo);
    }

    public void onShortcutsShow(CarUiShortcutsPopup carUiShortcutsPopup) {
        sCarUiShortcutsPopup = carUiShortcutsPopup;
    }

    private void dismissShortcutPopup() {
        // TODO (b/268563442): shortcut popup is set to be static since its
        // sometimes recreated when taskview is present, find out why
        if (sCarUiShortcutsPopup != null) {
            sCarUiShortcutsPopup.dismiss();
            sCarUiShortcutsPopup = null;
        }
    }

    @Override
    public void onAppPositionChanged(int newPosition, AppItem appItem) {
        mAppGridViewModel.saveAppOrder(newPosition, appItem);
    }


    private class AppGridOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mCurrentScrollOffset = mCurrentScrollOffset + (isHorizontal() ? dx : dy);
            mPageIndicator.updateOffset(mCurrentScrollOffset);
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            mCurrentScrollState = newState;
            mSnapCallback.setScrollState(mCurrentScrollState);
            switch (newState) {
                case RecyclerView.SCROLL_STATE_DRAGGING:
                    if (!mIsCurrentlyDragging) {
                        mDragCallback.cancelDragTasks();
                    }
                    dismissShortcutPopup();
                    mPageIndicator.animateAppearance();
                    break;

                case RecyclerView.SCROLL_STATE_SETTLING:
                    mPageIndicator.animateAppearance();
                    break;

                case RecyclerView.SCROLL_STATE_IDLE:
                    if (mIsCurrentlyDragging) {
                        mLayoutManager.setShouldLayoutChildren(false);
                    }
                    mPageIndicator.animateFading();
                    // in case the recyclerview was scrolled by rotary input, we need to handle
                    // focusing the correct element: either on the first or last element on page
                    mRecyclerView.maybeHandleRotaryFocus();
            }
        }
    }

    private class AppGridDragController {
        // TODO: (b/271320404) move DragController to separate directory called dragndrop and
        // migrate logic this class and AppItemViewHolder there.
        private final Handler mHandler;

        AppGridDragController() {
            mHandler = new Handler(getMainLooper());
        }

        void cancelDelayedPageFling() {
            mHandler.removeCallbacksAndMessages(null);
        }

        void postDelayedPageFling(@AppItemBoundDirection int exitDirection) {
            boolean scrollToNextPage = isHorizontal()
                    ? exitDirection == AppItemBoundDirection.RIGHT
                    : exitDirection == AppItemBoundDirection.BOTTOM;
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    if (mCurrentScrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        mAdapter.updatePageScrollDestination(scrollToNextPage);
                        mNextScrollDestination = mSnapCallback.getSnapPosition();

                        mLayoutManager.setShouldLayoutChildren(true);
                        mRecyclerView.smoothScrollToPosition(mNextScrollDestination);
                    }
                    // another delayed scroll will be queued to enable the user to input multiple
                    // page scrolls by holding the recyclerview at the app grid margin
                    postDelayedPageFling(exitDirection);
                }
            }, mOffPageHoverBeforeScrollMs);
        }
    }

    /**
     * Private onDragListener for handling dispatching off page scroll event when user holds the app
     * icon at the page margin.
     */
    private class AppGridDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            if (action == DragEvent.ACTION_DROP || action == DragEvent.ACTION_DRAG_ENDED) {
                mIsCurrentlyDragging = false;
                mAppGridDragController.cancelDelayedPageFling();
                mDragCallback.resetCallbackState();
                mLayoutManager.setShouldLayoutChildren(true);
                if (action == DragEvent.ACTION_DROP) {
                    return false;
                } else {
                    animateDropEnded(getDragSurface(event));
                }
            }
            return true;
        }
    }

    private void animateDropEnded(@Nullable SurfaceControl dragSurface) {
        if (dragSurface == null) {
            Log.d(TAG, "animateDropEnded, dragSurface unavailable");
            return;
        }
        // update default animation for the drag shadow after user lifts their finger
        SurfaceControl.Transaction txn = new SurfaceControl.Transaction();
        // set an animator to animate a delay before clearing the dragSurface
        ValueAnimator delayedDismissAnimator = ValueAnimator.ofFloat(0f, 1f);
        delayedDismissAnimator.setStartDelay(
                getResources().getInteger(R.integer.ms_drop_animation_delay));
        delayedDismissAnimator.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        txn.setAlpha(dragSurface, 0);
                        txn.apply();
                    }
                });
        delayedDismissAnimator.start();
    }

    private void setupTosBanner() {
        asLiveData(mAppGridViewModel.getShouldShowTosBanner()).observe(AppGridActivity.this,
                showBanner -> {
                    if (showBanner) {
                        mBanner.setVisibility(View.VISIBLE);
                        // Pre draw is required for animation to work.
                        mBanner.getViewTreeObserver().addOnPreDrawListener(
                                new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        mBanner.getViewTreeObserver().removeOnPreDrawListener(this);
                                        mBackgroundAnimationHelper.showBanner();
                                        return true;
                                    }
                                });
                    } else {
                        mBanner.setVisibility(View.GONE);
                    }
                });
        mBanner.setFirstButtonOnClickListener(v -> {
            Intent tosIntent = AppLauncherUtils.getIntentForTosAcceptanceFlow(v.getContext());
            AppLauncherUtils.launchApp(v.getContext(), tosIntent);
        });
        mBanner.setSecondButtonOnClickListener(
                v -> {
                    mBackgroundAnimationHelper.hideBanner();
                    mAppGridViewModel.saveTosBannerDismissalTime();
                });
    }

}
