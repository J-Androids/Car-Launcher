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

package com.android.car.carlauncher

import android.animation.ValueAnimator
import android.car.Car
import android.car.content.pm.CarPackageManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.media.CarMediaManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper.getMainLooper
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.RecyclerView
import com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection
import com.android.car.carlauncher.AppGridConstants.PageOrientation
import com.android.car.carlauncher.AppGridConstants.isHorizontal
import com.android.car.carlauncher.AppGridFragment.AppTypes.Companion.APP_TYPE_LAUNCHABLES
import com.android.car.carlauncher.AppGridFragment.AppTypes.Companion.APP_TYPE_MEDIA_SERVICES
import com.android.car.carlauncher.AppGridPageSnapper.AppGridPageSnapCallback
import com.android.car.carlauncher.AppGridPageSnapper.PageSnapListener
import com.android.car.carlauncher.AppGridViewModel.Companion.provideFactory
import com.android.car.carlauncher.datasources.AppOrderDataSource
import com.android.car.carlauncher.datasources.AppOrderProtoDataSourceImpl
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSource
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSourceImpl
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSourceImpl.MirroringServiceConnection
import com.android.car.carlauncher.datasources.LauncherActivitiesDataSource
import com.android.car.carlauncher.datasources.LauncherActivitiesDataSourceImpl
import com.android.car.carlauncher.datasources.MediaTemplateAppsDataSource
import com.android.car.carlauncher.datasources.MediaTemplateAppsDataSourceImpl
import com.android.car.carlauncher.datasources.UXRestrictionDataSource
import com.android.car.carlauncher.datasources.UXRestrictionDataSourceImpl
import com.android.car.carlauncher.datasources.restricted.DisabledAppsDataSource
import com.android.car.carlauncher.datasources.restricted.DisabledAppsDataSourceImpl
import com.android.car.carlauncher.datasources.restricted.TosDataSource
import com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl
import com.android.car.carlauncher.datastore.launcheritem.LauncherItemListSource
import com.android.car.carlauncher.pagination.PageMeasurementHelper
import com.android.car.carlauncher.pagination.PaginationController
import com.android.car.carlauncher.pagination.PaginationController.DimensionUpdateCallback
import com.android.car.carlauncher.pagination.PaginationController.DimensionUpdateListener
import com.android.car.carlauncher.recyclerview.AppGridAdapter
import com.android.car.carlauncher.recyclerview.AppGridAdapter.AppGridAdapterListener
import com.android.car.carlauncher.recyclerview.AppGridItemAnimator
import com.android.car.carlauncher.recyclerview.AppGridLayoutManager
import com.android.car.carlauncher.recyclerview.AppItemViewHolder.AppItemDragCallback
import com.android.car.carlauncher.recyclerview.AppItemViewHolder.AppItemDragListener
import com.android.car.carlauncher.repositories.AppGridRepository
import com.android.car.carlauncher.repositories.AppGridRepositoryImpl
import com.android.car.carlauncher.repositories.appactions.AppLaunchProviderFactory
import com.android.car.carlauncher.repositories.appactions.AppShortcutsFactory
import com.android.car.carlauncher.repositories.appactions.AppShortcutsFactory.ShortcutsListener
import com.android.car.hidden.apis.HiddenApiAccess
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO

/**
 * Fragment which renders the Apps based on the [Mode] provided in the [setArguments]
 *
 * To create an instance of this Fragment use [newInstance]
 */
class AppGridFragment : Fragment(), PageSnapListener, AppItemDragListener, DimensionUpdateListener,
    AppGridAdapterListener {

    private lateinit var car: Car
    private lateinit var mode: Mode
    private lateinit var snapCallback: AppGridPageSnapCallback
    private lateinit var dragCallback: AppItemDragCallback
    private lateinit var appGridDragController: AppGridDragController
    private lateinit var appGridRecyclerView: AppGridRecyclerView
    private lateinit var layoutManager: AppGridLayoutManager
    private lateinit var pageIndicator: PageIndicator
    private lateinit var adapter: AppGridAdapter
    private lateinit var paginationController: PaginationController
    private lateinit var backgroundAnimationHelper: BackgroundAnimationHelper
    private lateinit var appGridViewModel: AppGridViewModel
    private lateinit var banner: Banner

    private var appGridMarginHorizontal = 0
    private var appGridMarginVertical = 0
    private var appGridWidth = 0
    private var appGridHeight = 0
    private var offPageHoverBeforeScrollMs = 0L
    private var numOfCols = 0
    private var numOfRows = 0
    private var nextScrollDestination = 0
    private var currentScrollOffset = 0
    private var currentScrollState = 0
    private var isCurrentlyDragging = false
    private var carUiShortcutsPopup: CarUiShortcutsPopup? = null

    @PageOrientation
    private var pageOrientation = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.app_grid_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        car = Car.createCar(requireContext()) ?: throw IllegalStateException("Car not initialized")
        mode = Mode.valueOf(requireArguments().getString(MODE_INTENT_EXTRA, Mode.ALL_APPS.name))
        initViewModel()
        updateMode(mode)

        snapCallback = AppGridPageSnapCallback(this)
        dragCallback = AppItemDragCallback(this)

        numOfCols = resources.getInteger(R.integer.car_app_selector_column_number)
        numOfRows = resources.getInteger(R.integer.car_app_selector_row_number)
        appGridDragController = AppGridDragController()
        offPageHoverBeforeScrollMs = resources.getInteger(
            R.integer.ms_off_page_hover_before_scroll
        ).toLong()

        pageOrientation =
            if (resources.getBoolean(R.bool.use_vertical_app_grid)) {
                PageOrientation.VERTICAL
            } else {
                PageOrientation.HORIZONTAL
            }

        appGridRecyclerView = view.requireViewById(R.id.apps_grid)
        appGridRecyclerView.isFocusable = false
        layoutManager =
            AppGridLayoutManager(requireContext(), numOfCols, numOfRows, pageOrientation)
        appGridRecyclerView.layoutManager = layoutManager

        val pageSnapper = AppGridPageSnapper(
            requireContext(),
            numOfCols,
            numOfRows,
            snapCallback
        )
        pageSnapper.attachToRecyclerView(appGridRecyclerView)

        appGridRecyclerView.itemAnimator = AppGridItemAnimator()

        // hide the default scrollbar and replace it with a visual page indicator
        appGridRecyclerView.isVerticalScrollBarEnabled = false
        appGridRecyclerView.isHorizontalScrollBarEnabled = false
        appGridRecyclerView.addOnScrollListener(AppGridOnScrollListener())

        // TODO: (b/271637411) move this to be contained in a scroll controller
        pageIndicator = view.requireViewById(R.id.page_indicator)
        val pageIndicatorContainer: FrameLayout =
            view.requireViewById(R.id.page_indicator_container)
        pageIndicator.setContainer(pageIndicatorContainer)

        // recycler view is set to LTR to prevent layout manager from reassigning layout direction.
        // instead, PageIndexinghelper will determine the grid index based on the system layout
        // direction and provide LTR mapping at adapter level.
        appGridRecyclerView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        pageIndicatorContainer.layoutDirection = View.LAYOUT_DIRECTION_LTR

        // we create but do not attach the adapter to recyclerview until view tree layout is
        // complete and the total size of the app grid is measureable.
        adapter = AppGridAdapter(
            requireContext(), numOfCols, numOfRows, dragCallback, snapCallback, this, mode
        )

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                // scroll state will need to be updated after item has been dropped
                nextScrollDestination = snapCallback.snapPosition
                updateScrollState()
            }
        })
        appGridRecyclerView.adapter = adapter

        appGridViewModel.getAppList().asLiveData().observe(
            viewLifecycleOwner
        ) { appItems: List<AppItem?>? ->
            adapter.setLauncherItems(appItems)
            nextScrollDestination = snapCallback.snapPosition
            updateScrollState()
        }

        appGridViewModel.requiresDistractionOptimization().asLiveData().observe(
            viewLifecycleOwner
        ) { uxRestrictions: Boolean ->
            handleDistractionOptimization(
                uxRestrictions
            )
        }

        // set drag listener and global layout listener, which will dynamically adjust app grid
        // height and width depending on device screen size. ize.
        if (resources.getBoolean(R.bool.config_allow_reordering)) {
            appGridRecyclerView.setOnDragListener(AppGridDragListener())
        }

        // since some measurements for window size may not be available yet during onCreate or may
        // later change, we add a listener that redraws the app grid when window size changes.
        val windowBackground: LinearLayout = view.requireViewById(R.id.apps_grid_background)
        windowBackground.orientation =
            if (isHorizontal(pageOrientation)) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val dimensionUpdateCallback = DimensionUpdateCallback()
        dimensionUpdateCallback.addListener(appGridRecyclerView)
        dimensionUpdateCallback.addListener(pageIndicator)
        dimensionUpdateCallback.addListener(this)
        paginationController = PaginationController(windowBackground, dimensionUpdateCallback)

        banner = view.requireViewById(R.id.tos_banner)

        backgroundAnimationHelper = BackgroundAnimationHelper(windowBackground, banner)

        setupTosBanner()
    }

    /**
     * Updates the state of the app grid components depending on the driving state.
     */
    private fun handleDistractionOptimization(requiresDistractionOptimization: Boolean) {
        adapter.setIsDistractionOptimizationRequired(requiresDistractionOptimization)
        if (requiresDistractionOptimization) {
            // if the user start driving while drag is in action, we cancel existing drag operations
            if (isCurrentlyDragging) {
                isCurrentlyDragging = false
                layoutManager.setShouldLayoutChildren(true)
                appGridRecyclerView.cancelDragAndDrop()
            }
            dismissShortcutPopup()
        }
    }

    private fun initViewModel() {
        val launcherActivities: LauncherActivitiesDataSource = LauncherActivitiesDataSourceImpl(
            requireContext().getSystemService(LauncherApps::class.java),
            { broadcastReceiver: BroadcastReceiver?, intentFilter: IntentFilter? ->
                requireContext().registerReceiver(broadcastReceiver, intentFilter)
            },
            { broadcastReceiver: BroadcastReceiver? ->
                requireContext().unregisterReceiver(broadcastReceiver)
            },
            Process.myUserHandle(),
            requireContext().applicationContext.resources,
            Default
        )
        val mediaTemplateApps: MediaTemplateAppsDataSource = MediaTemplateAppsDataSourceImpl(
            requireContext().packageManager,
            requireContext().applicationContext,
            Default
        )
        val disabledApps: DisabledAppsDataSource = DisabledAppsDataSourceImpl(
            requireContext().contentResolver,
            requireContext().packageManager,
            IO
        )
        val tosApps: TosDataSource = TosDataSourceImpl(
            requireContext().contentResolver,
            requireContext().packageManager,
            IO
        )
        val controlCenterMirroringDataSource: ControlCenterMirroringDataSource =
            ControlCenterMirroringDataSourceImpl(
                requireContext().applicationContext.resources,
                { intent: Intent, serviceConnection: MirroringServiceConnection, flags: Int ->
                    requireContext().bindService(intent, serviceConnection, flags)
                },
                { serviceConnection: MirroringServiceConnection ->
                    requireContext().unbindService(serviceConnection)
                },
                requireContext().packageManager,
                IO
            )
        val uxRestrictionDataSource: UXRestrictionDataSource = UXRestrictionDataSourceImpl(
            requireContext(),
            requireNotNull(car.getCarManager(CarUxRestrictionsManager::class.java)),
            requireNotNull(car.getCarManager(CarPackageManager::class.java)),
            requireContext().getSystemService(MediaSessionManager::class.java),
            requireContext().applicationContext.resources,
            Default
        )
        val appOrderDataSource: AppOrderDataSource = AppOrderProtoDataSourceImpl(
            LauncherItemListSource(requireContext().filesDir, "order.data"),
            IO
        )
        val packageManager: PackageManager = requireContext().packageManager
        val launchProviderFactory = AppLaunchProviderFactory(
            requireNotNull(car.getCarManager(CarMediaManager::class.java)),
            mode.openMediaCenter,
            {
                activity?.finish()
            },
            requireContext().packageManager
        )

        val appShortcutsFactory = AppShortcutsFactory(
            requireNotNull(car.getCarManager(CarMediaManager::class.java)),
            emptySet(),
            object : ShortcutsListener {
                override fun onShortcutsShow(carUiShortcutsPopup: CarUiShortcutsPopup) {
                    this@AppGridFragment.carUiShortcutsPopup = carUiShortcutsPopup
                }
            }
        )
        val bgDispatcher = Default
        val repo: AppGridRepository = AppGridRepositoryImpl(
            launcherActivities, mediaTemplateApps,
            disabledApps, tosApps, controlCenterMirroringDataSource, uxRestrictionDataSource,
            appOrderDataSource, packageManager, launchProviderFactory, appShortcutsFactory,
            requireContext().getSystemService(UserManager::class.java), bgDispatcher
        )

        appGridViewModel = ViewModelProvider(
            this,
            provideFactory(repo, requireActivity().application, this, null)
        )[AppGridViewModel::class.java]
    }

    private fun animateDropEnded(dragSurface: SurfaceControl?) {
        if (dragSurface == null) {
            if (DEBUG_BUILD) {
                Log.d(TAG, "animateDropEnded, dragSurface unavailable")
            }
            return
        }
        // update default animation for the drag shadow after user lifts their finger
        val txn = SurfaceControl.Transaction()
        // set an animator to animate a delay before clearing the dragSurface
        val delayedDismissAnimator = ValueAnimator.ofFloat(0f, 1f)
        delayedDismissAnimator.startDelay =
            resources.getInteger(R.integer.ms_drop_animation_delay).toLong()
        delayedDismissAnimator.addUpdateListener {
            txn.setAlpha(dragSurface, 0f)
            txn.apply()
        }
        delayedDismissAnimator.start()
    }

    private fun setupTosBanner() {
        appGridViewModel.getShouldShowTosBanner().asLiveData()
            .observe(
                viewLifecycleOwner
            ) { showBanner: Boolean ->
                if (showBanner) {
                    banner.visibility = View.VISIBLE
                    // Pre draw is required for animation to work.
                    banner.viewTreeObserver.addOnPreDrawListener(
                        object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                banner.viewTreeObserver.removeOnPreDrawListener(this)
                                backgroundAnimationHelper.showBanner()
                                return true
                            }
                        }
                    )
                } else {
                    banner.visibility = View.GONE
                }
            }
        banner.setFirstButtonOnClickListener { v: View ->
            val tosIntent =
                AppLauncherUtils.getIntentForTosAcceptanceFlow(v.context)
            AppLauncherUtils.launchApp(v.context, tosIntent)
        }
        banner.setSecondButtonOnClickListener { _ ->
            backgroundAnimationHelper.hideBanner()
            appGridViewModel.saveTosBannerDismissalTime()
        }
    }

    /**
     * Updates the scroll state after receiving data changes, such as new apps being added or
     * reordered, and when user returns to launcher onResume.
     *
     * Additionally, notify page indicator to handle resizing in case new app addition creates a
     * new page or deleted a page.
     */
    fun updateScrollState() {
        // TODO(b/271637411): move this method into a scroll controller
        // to calculate how many pages we need to offset, we use the scroll offset anchor position
        // as item count and map to the page which the anchor is on.
        val offsetPageCount = adapter.getPageCount(nextScrollDestination + 1) - 1
        appGridRecyclerView.suppressLayout(false)
        currentScrollOffset =
            offsetPageCount * if (isHorizontal(pageOrientation)) {
                appGridWidth + 2 * appGridMarginHorizontal
            } else {
                appGridHeight + 2 * appGridMarginVertical
            }
        layoutManager.scrollToPositionWithOffset(offsetPageCount * numOfRows * numOfCols, 0)
        pageIndicator.updateOffset(currentScrollOffset)
        pageIndicator.updatePageCount(adapter.pageCount)
    }

    /**
     * Change the mode of the apps shown in the AppGrid
     * @see [Mode]
     */
    fun updateMode(mode: Mode) {
        this.mode = mode
        appGridViewModel.updateMode(mode)
    }

    private inner class AppGridOnScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            currentScrollOffset += if (isHorizontal(pageOrientation)) dx else dy
            pageIndicator.updateOffset(currentScrollOffset)
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            currentScrollState = newState
            snapCallback.scrollState = currentScrollState
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    if (!isCurrentlyDragging) {
                        dragCallback.cancelDragTasks()
                    }
                    dismissShortcutPopup()
                    pageIndicator.animateAppearance()
                }

                RecyclerView.SCROLL_STATE_SETTLING -> pageIndicator.animateAppearance()
                RecyclerView.SCROLL_STATE_IDLE -> {
                    if (isCurrentlyDragging) {
                        layoutManager.setShouldLayoutChildren(false)
                    }
                    pageIndicator.animateFading()
                    // in case the recyclerview was scrolled by rotary input, we need to handle
                    // focusing the correct element: either on the first or last element on page
                    appGridRecyclerView.maybeHandleRotaryFocus()
                }
            }
        }
    }

    private fun dismissShortcutPopup() {
        carUiShortcutsPopup?.let {
            it.dismiss()
            carUiShortcutsPopup = null
        }
    }

    override fun onPause() {
        dismissShortcutPopup()
        super.onPause()
    }

    override fun onDestroy() {
        if (car.isConnected) {
            car.disconnect()
        }
        super.onDestroy()
    }

    override fun onSnapToPosition(gridPosition: Int) {
        nextScrollDestination = gridPosition
    }

    override fun onDimensionsUpdated(
        pageDimens: PageMeasurementHelper.PageDimensions,
        gridDimens: PageMeasurementHelper.GridDimensions
    ) {
        // TODO(b/271637411): move this method into a scroll controller
        appGridMarginHorizontal = pageDimens.marginHorizontalPx
        appGridMarginVertical = pageDimens.marginVerticalPx
        appGridWidth = gridDimens.gridWidthPx
        appGridHeight = gridDimens.gridHeightPx
    }

    override fun onAppPositionChanged(newPosition: Int, appItem: AppItem) {
        appGridViewModel.saveAppOrder(newPosition, appItem)
    }

    override fun onItemLongPressed(longPressed: Boolean) {
        // after the user long presses the app icon, scrolling should be disabled until long press
        // is canceled as to allow MotionEvent to be interpreted as attempt to drag the app icon.
        appGridRecyclerView.suppressLayout(longPressed)
    }

    override fun onItemSelected(gridPositionFrom: Int) {
        isCurrentlyDragging = true
        layoutManager.setShouldLayoutChildren(false)
        adapter.setDragStartPoint(gridPositionFrom)
        dismissShortcutPopup()
    }

    override fun onItemDragged() {
        appGridDragController.cancelDelayedPageFling()
    }

    override fun onDragExited(gridPosition: Int, exitDirection: Int) {
        if (adapter.getOffsetBoundDirection(gridPosition) == exitDirection) {
            appGridDragController.postDelayedPageFling(exitDirection)
        }
    }

    override fun onItemDropped(gridPositionFrom: Int, gridPositionTo: Int) {
        layoutManager.setShouldLayoutChildren(true)
        adapter.moveAppItem(gridPositionFrom, gridPositionTo)
    }

    private inner class AppGridDragController() {
        // TODO: (b/271320404) move DragController to separate directory called dragndrop and
        // migrate logic this class and AppItemViewHolder there.
        private val handler: Handler = Handler(getMainLooper())

        fun cancelDelayedPageFling() {
            handler.removeCallbacksAndMessages(null)
        }

        fun postDelayedPageFling(@AppItemBoundDirection exitDirection: Int) {
            val scrollToNextPage =
                if (isHorizontal(pageOrientation)) {
                    exitDirection == AppItemBoundDirection.RIGHT
                } else {
                    exitDirection == AppItemBoundDirection.BOTTOM
                }
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                if (currentScrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    adapter.updatePageScrollDestination(scrollToNextPage)
                    nextScrollDestination = snapCallback.snapPosition
                    layoutManager.setShouldLayoutChildren(true)
                    appGridRecyclerView.smoothScrollToPosition(nextScrollDestination)
                }
                // another delayed scroll will be queued to enable the user to input multiple
                // page scrolls by holding the recyclerview at the app grid margin
                postDelayedPageFling(exitDirection)
            }, offPageHoverBeforeScrollMs)
        }
    }

    /**
     * Private onDragListener for handling dispatching off page scroll event when user holds the app
     * icon at the page margin.
     */
    private inner class AppGridDragListener : View.OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            val action = event.action
            if (action == DragEvent.ACTION_DROP || action == DragEvent.ACTION_DRAG_ENDED) {
                isCurrentlyDragging = false
                appGridDragController.cancelDelayedPageFling()
                dragCallback.resetCallbackState()
                layoutManager.setShouldLayoutChildren(true)
                if (action == DragEvent.ACTION_DROP) {
                    return false
                } else {
                    animateDropEnded(HiddenApiAccess.getDragSurface(event))
                }
            }
            return true
        }
    }

    annotation class AppTypes {
        companion object {
            const val APP_TYPE_LAUNCHABLES = 1
            const val APP_TYPE_MEDIA_SERVICES = 2
        }
    }

    enum class Mode(
        @field:StringRes @param:StringRes val titleStringId: Int,
        @field:AppTypes @param:AppTypes val appTypes: Int,
        val openMediaCenter: Boolean
    ) {
        ALL_APPS(
            R.string.app_launcher_title_all_apps,
            APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
            true
        ),
        MEDIA_ONLY(
            R.string.app_launcher_title_media_only,
            APP_TYPE_MEDIA_SERVICES,
            true
        ),
        MEDIA_POPUP(
            R.string.app_launcher_title_media_only,
            APP_TYPE_MEDIA_SERVICES,
            false
        )
    }

    companion object {
        const val TAG = "AppGridFragment"
        const val DEBUG_BUILD = false
        const val MODE_INTENT_EXTRA = "com.android.car.carlauncher.mode"

        @JvmStatic
        fun newInstance(mode: Mode): AppGridFragment {
            return AppGridFragment().apply {
                arguments = Bundle().apply {
                    putString(MODE_INTENT_EXTRA, mode.name)
                }
            }
        }
    }
}
