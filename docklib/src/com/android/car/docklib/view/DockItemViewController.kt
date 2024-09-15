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

package com.android.car.docklib.view

import android.content.res.ColorStateList
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.util.Log
import androidx.core.animation.Animator
import com.android.car.docklib.data.DockAppItem
import com.android.car.docklib.view.animation.ExcitementAnimationHelper
import com.google.android.material.imageview.ShapeableImageView
import java.util.EnumSet
import kotlin.math.floor

/**
 * Controller to help manage states for individual DockItemViews.
 */
class DockItemViewController(
    private val staticIconStrokeWidth: Float,
    private val dynamicIconStrokeWidth: Float,
    private val excitedIconStrokeWidth: Float,
    private val staticIconStrokeColor: Int,
    private val excitedIconStrokeColor: Int,
    private val restrictedIconStrokeColor: Int,
    private val defaultIconColor: Int,
    private val excitedColorFilter: ColorFilter,
    private val restrictedColorFilter: ColorFilter,
    private val excitedIconColorFilterAlpha: Float,
    private val exciteAnimationDuration: Int,
) {

    companion object {
        private val TAG = DockItemViewController::class.simpleName
        private val DEBUG = Build.isDebuggable()
        private const val DEFAULT_STROKE_WIDTH = 0f
        private const val INITIAL_COLOR_FILTER_ALPHA = 0f
    }

    private enum class TypeStates {
        DYNAMIC, STATIC
    }

    private enum class OptionalStates {
        EXCITED, UPDATING, RESTRICTED, ACTIVE_MEDIA
    }

    private var dynamicIconStrokeColor: Int = defaultIconColor
    private var updatingColor: Int = defaultIconColor
    private var exciteAnimator: Animator? = null

    private var typeState: Enum<TypeStates> = TypeStates.STATIC
    private val optionalState: EnumSet<OptionalStates> = EnumSet.noneOf(OptionalStates::class.java)

    /**
     * Setter to set if the DockItem is dynamic.
     */
    fun setDynamic(dynamicIconStrokeColor: Int) {
        typeState = TypeStates.DYNAMIC
        this.dynamicIconStrokeColor = dynamicIconStrokeColor
    }

    /**
     * Setter to set if the DockItem is static.
     */
    fun setStatic() {
        typeState = TypeStates.STATIC
        this.dynamicIconStrokeColor = defaultIconColor
    }

    /**
     * Setter to set if the DockItem is excited. Returns true if the state was changed.
     */
    fun setExcited(isExcited: Boolean): Boolean {
        if ((isExcited && optionalState.contains(OptionalStates.EXCITED)) ||
            (!isExcited && !optionalState.contains(OptionalStates.EXCITED))
        ) {
            return false
        }

        if (isExcited) {
            optionalState.add(OptionalStates.EXCITED)
        } else {
            optionalState.remove(OptionalStates.EXCITED)
        }
        return true
    }

    /**
     * Setter to set if the DockItem is updating to another [DockAppItem]
     * @param updatingColor color to use when app is updating. Generally the icon color of the next
     * [DockAppItem]
     * @return Returns true if the state was changed, false otherwise
     */
    fun setUpdating(isUpdating: Boolean, updatingColor: Int?): Boolean {
        if ((isUpdating && optionalState.contains(OptionalStates.UPDATING)) ||
            (!isUpdating && !optionalState.contains(OptionalStates.UPDATING))
        ) {
            return false
        }

        if (isUpdating) {
            optionalState.add(OptionalStates.UPDATING)
        } else {
            optionalState.remove(OptionalStates.UPDATING)
        }
        this.updatingColor = updatingColor ?: defaultIconColor
        return true
    }

    /**
     * Setter to set if the DockItem is restricted. Returns true if the state was changed.
     */
    fun setRestricted(isRestricted: Boolean): Boolean {
        if ((isRestricted && optionalState.contains(OptionalStates.RESTRICTED)) ||
            (!isRestricted && !optionalState.contains(OptionalStates.RESTRICTED))
        ) {
            return false
        }

        if (isRestricted) {
            optionalState.add(OptionalStates.RESTRICTED)
        } else {
            optionalState.remove(OptionalStates.RESTRICTED)
        }
        return true
    }

    /** Tracks whether the app item has an active media session or not */
    fun setHasActiveMediaSession(
        hasMediaSession: Boolean
    ): Boolean {
        if ((hasMediaSession && optionalState.contains(OptionalStates.ACTIVE_MEDIA)) ||
            (!hasMediaSession && !optionalState.contains(OptionalStates.ACTIVE_MEDIA))
        ) {
            return false
        }

        if (hasMediaSession) {
            optionalState.add(OptionalStates.ACTIVE_MEDIA)
        } else {
            optionalState.remove(OptionalStates.ACTIVE_MEDIA)
        }
        return true
    }

    /** @return whether the view should be restricted or not */
    fun shouldBeRestricted(): Boolean {
        return optionalState.contains(OptionalStates.RESTRICTED) &&
                !optionalState.contains(OptionalStates.ACTIVE_MEDIA)
    }

    /**
     * Updates the [appIcon] based on the current state
     */
    fun updateViewBasedOnState(appIcon: ShapeableImageView) {
        if (DEBUG) {
            Log.d(
                TAG,
                "updateViewBasedOnState, typeState: $typeState, optionalState: $optionalState"
            )
        }
        if (exciteAnimator != null) {
            exciteAnimator?.cancel()
            exciteAnimator = null
        }
        appIcon.strokeColor = ColorStateList.valueOf(getStrokeColor())
        appIcon.strokeWidth = getStrokeWidth()
        appIcon.colorFilter = getColorFilter()
        val cp = getContentPadding()
        // ContentPadding should not be set before the measure phase of the view otherwise it might
        // set incorrect padding values on the view.
        appIcon.post { appIcon.setContentPadding(cp, cp, cp, cp) }
    }

    /**
     * Animate the [appIcon] to be excited or reset after being excited.
     */
    fun animateAppIconExcited(appIcon: ShapeableImageView) {
        val isAnimationOngoing = exciteAnimator?.isRunning ?: false
        if (DEBUG) {
            Log.d(
                TAG,
                "Excite animation{ " +
                        "isExciting: ${optionalState.contains(OptionalStates.EXCITED)}, " +
                        "isAnimationOngoing: $isAnimationOngoing }"
            )
        }
        exciteAnimator?.cancel()

        val toStrokeWidth: Float = getStrokeWidth()
        val toContentPadding: Int = getContentPadding()
        val toStrokeColor: Int = getStrokeColor()
        val toColorFilterAlpha: Float = if (optionalState.contains(OptionalStates.EXCITED)) {
            excitedIconColorFilterAlpha
        } else {
            INITIAL_COLOR_FILTER_ALPHA
        }

        val successCallback = {
            exciteAnimator = null
            updateViewBasedOnState(appIcon)
        }

        val failureCallback = {
            exciteAnimator = null
            updateViewBasedOnState(appIcon)
        }

        exciteAnimator = ExcitementAnimationHelper.getExcitementAnimator(
            appIcon,
            exciteAnimationDuration.toLong(),
            toStrokeWidth,
            toStrokeColor,
            toContentPadding,
            toColorFilterAlpha,
            successCallback,
            failureCallback
        )
        exciteAnimator?.start()
    }

    private fun getStrokeColor(): Int {
        if (optionalState.contains(OptionalStates.UPDATING)) {
            return updatingColor
        } else if (shouldBeRestricted()) {
            return restrictedIconStrokeColor
        } else if (optionalState.contains(OptionalStates.EXCITED)) {
            return excitedIconStrokeColor
        } else if (typeState == TypeStates.STATIC) {
            return staticIconStrokeColor
        } else if (typeState == TypeStates.DYNAMIC) {
            return dynamicIconStrokeColor
        }
        return defaultIconColor
    }

    private fun getStrokeWidth(): Float {
        if (optionalState.contains(OptionalStates.EXCITED)) {
            return excitedIconStrokeWidth
        } else if (typeState == TypeStates.STATIC) {
            return staticIconStrokeWidth
        } else if (typeState == TypeStates.DYNAMIC) {
            return dynamicIconStrokeWidth
        }
        return DEFAULT_STROKE_WIDTH
    }

    private fun getContentPadding(): Int {
        return getContentPaddingFromStrokeWidth(getStrokeWidth())
    }

    private fun getColorFilter(): ColorFilter? {
        if (optionalState.contains(OptionalStates.UPDATING)) {
            return PorterDuffColorFilter(updatingColor, PorterDuff.Mode.SRC_OVER)
        } else if (shouldBeRestricted()){
            return restrictedColorFilter
        } else if (optionalState.contains(OptionalStates.EXCITED)) {
            return excitedColorFilter
        }
        return null
    }

    private fun getContentPaddingFromStrokeWidth(strokeWidth: Float): Int =
        floor(strokeWidth / 2).toInt()
}
