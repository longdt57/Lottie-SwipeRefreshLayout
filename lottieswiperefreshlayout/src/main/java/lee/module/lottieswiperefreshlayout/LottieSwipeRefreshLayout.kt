/*
* Copyright 2018 The Android Open Source Project
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
package lee.module.lottieswiperefreshlayout

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.AbsListView
import android.widget.ListView
import androidx.annotation.*
import androidx.core.view.*
import androidx.core.widget.ListViewCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import java.lang.Exception
import kotlin.math.abs

/**
 * @see [androidx.swiperefreshlayout.widget.SwipeRefreshLayout]
 *
 * The SwipeRefreshLayout should be used whenever the user can refresh the
 * contents of a view via a vertical swipe gesture. The activity that
 * instantiates this view should add an OnRefreshListener to be notified
 * whenever the swipe to refresh gesture is completed. The SwipeRefreshLayout
 * will notify the listener each and every time the gesture is completed again;
 * the listener is responsible for correctly determining when to actually
 * initiate a refresh of its content. If the listener determines there should
 * not be a refresh, it must call setRefreshing(false) to cancel any visual
 * indication of a refresh. If an activity wishes to show just the progress
 * animation, it should call setRefreshing(true). To disable the gesture and
 * progress animation, call setEnabled(false) on the view.
 *
 *
 * This layout should be made the parent of the view that will be refreshed as a
 * result of the gesture and can only support one direct child. This view will
 * also be made the target of the gesture and will be forced to match both the
 * width and the height supplied in this layout. The SwipeRefreshLayout does not
 * provide accessibility events; instead, a menu item must be provided to allow
 * refresh of the content wherever this gesture is used.
 *
 * How to customize:
 * [createLottieView] override to custom lottie
 * [indicatorOverlay] The Lottie should Overlay the content or not
 * [setSizePx] to change the lottie size
 * [mScale] Whether enable scale animation
 * [mAlpha] Whether enable alpha animation
 *
 */
open class LottieSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    ViewGroup(context, attrs, defStyle), NestedScrollingParent3, NestedScrollingParent2,
    NestedScrollingChild3, NestedScrollingChild2, NestedScrollingParent, NestedScrollingChild {

    /**
     * Similar to [androidx.swiperefreshlayout.widget.SwipeRefreshLayout.mCircleView]
     * and [androidx.swiperefreshlayout.widget.SwipeRefreshLayout.mProgress]
     */
    protected lateinit var lottieAnimationView: LottieAnimationView

    private var mTarget: View? = null // the target of the gesture, or the contentView inside swiperefreshlayout

    protected var mListener: OnRefreshListener? = null
    protected var mRefreshing = false
    private val mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var mTotalDragDistance = -1f

    // If nested scrolling is enabled, the total amount that needed to be
    // consumed by this as the nested scrolling parent is used in place of the
    // overscroll determined by MOVE events in the onTouch handler
    private var mTotalUnconsumed = 0f
    private val mNestedScrollingParentHelper: NestedScrollingParentHelper by lazy { NestedScrollingParentHelper(this) }
    private val mNestedScrollingChildHelper: NestedScrollingChildHelper by lazy { NestedScrollingChildHelper(this) }
    private val mParentScrollConsumed = IntArray(2)
    private val mParentOffsetInWindow = IntArray(2)

    // Used for calls from old versions of onNestedScroll to v3 version of onNestedScroll. This only
    // exists to prevent GC costs that are present before API 21.
    private val mNestedScrollingV2ConsumedCompat = IntArray(2)
    private var mNestedScrollInProgress = false

    private val mMediumAnimationDuration: Int = resources.getInteger(android.R.integer.config_mediumAnimTime)
    private var mCurrentTargetOffsetTop: Int = 0

    private var mInitialMotionY = 0f
    private var mInitialDownY = 0f
    private var mIsBeingDragged = false
    private var mActivePointerId = INVALID_POINTER

    // Whether this item is scaled up rather than clipped
    protected var mScale = false

    // Whether this item is alpha up rather than clipped
    protected var mAlpha = false

    // Whether to overlay the indicator on top of the content or not
    var indicatorOverlay = false

    // Whether auto tint color by theme
    var autoTintColor = false
        set(value) {
            field = value
            enableAutoTintColor()
        }

    // Target is returning to its start offset because it was cancelled or a
    // refresh was triggered.
    private var mReturningToStart = false
    private val mDecelerateInterpolator: DecelerateInterpolator

    private var lottieAnimationViewIndex = -1

    protected var mFrom = 0
    protected var mStartingScale = 0f

    /**
     * @return The offset in pixels from the top of this view at which the progress spinner should
     * appear.
     */

    protected var mOriginalOffsetTop: Int = 0

    /**
     * @return The offset in pixels from the top of this view at which the progress spinner should
     * come to rest after a successful swipe gesture.
     */
    private var mSpinnerOffsetEnd: Int = 0

    protected var mCustomSlingshotDistance = 0

    private var mAlphaStartAnimation: Animation? = null
    private var mAlphaMaxAnimation: Animation? = null

    protected var mNotify = false

    // The lottie size
    private var mCircleDiameter = dpToPx(CIRCLE_DIAMETER)

    // Whether the client has set a custom starting position;
    protected var mUsingCustomStart = false
    private var mChildScrollUpCallback: OnChildScrollUpCallback? = null

    /**
     * @see .setLegacyRequestDisallowInterceptTouchEventEnabled
     */
    private var mEnableLegacyRequestDisallowInterceptTouch = false

    private val mRefreshListener: AnimationListener = object : AnimationListener {
        override fun onAnimationStart(animation: Animation) {}
        override fun onAnimationRepeat(animation: Animation) {}
        override fun onAnimationEnd(animation: Animation) {
            if (mRefreshing) {
                // Make sure the progress view is fully visible
                lottieAnimationView.imageAlpha = MAX_ALPHA
                lottieAnimationView.playAnimation()
                if (mNotify) {
                    mListener?.onRefresh()
                }
                mCurrentTargetOffsetTop = lottieAnimationView.top
            } else {
                reset()
            }
        }
    }

    /**
     * Constructor that is called when inflating SwipeRefreshLayout from XML.
     */
    init {
        this.setWillNotDraw(false)

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.LottieSwipeRefreshLayout, defStyle, 0)
        createProgressView()
        initLottie(a)
        initOffset(a)

        mDecelerateInterpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)

        isChildrenDrawingOrderEnabled = true
        isNestedScrollingEnabled = true

        moveToStart(1.0f)

        isEnabled = a.getBoolean(R.styleable.LottieSwipeRefreshLayout_android_enabled, true)
        mScale = a.getBoolean(R.styleable.LottieSwipeRefreshLayout_lottie_srl_scale_enabled, mScale)
        mAlpha = a.getBoolean(R.styleable.LottieSwipeRefreshLayout_lottie_srl_scale_enabled, mAlpha)
        indicatorOverlay = a.getBoolean(R.styleable.LottieSwipeRefreshLayout_lottie_srl_indicator_overlay, indicatorOverlay)
        autoTintColor = a.getBoolean(R.styleable.LottieSwipeRefreshLayout_lottie_srl_auto_tint, autoTintColor)
        a.recycle()
    }

    // Lottie
    private fun initLottie(style: TypedArray) {
        val lottieRawRes = style.getResourceId(R.styleable.LottieSwipeRefreshLayout_lottie_srl_rawRes, R.raw.loader_zm)
        lottieAnimationView.setAnimation(lottieRawRes)

        val lottieSize = style.getDimensionPixelOffset(R.styleable.LottieSwipeRefreshLayout_lottie_srl_size, mCircleDiameter)
        setSizePx(lottieSize)
    }

    // Offset
    private fun initOffset(style: TypedArray) {
        val paddingTop = style.getDimensionPixelOffset(R.styleable.LottieSwipeRefreshLayout_lottie_srl_padding_top, dpToPx(INDICATOR_PADDING_TOP))
        val paddingBottom =
            style.getDimensionPixelOffset(R.styleable.LottieSwipeRefreshLayout_lottie_srl_padding_bottom, dpToPx(INDICATOR_PADDING_BOTTOM))

        mOriginalOffsetTop = -mCircleDiameter - paddingBottom
        mSpinnerOffsetEnd = mCircleDiameter + paddingTop + paddingBottom

        // the absolute offset has to take into account that the circle starts at an offset
        mTotalDragDistance = mSpinnerOffsetEnd.toFloat()
        mCurrentTargetOffsetTop = mOriginalOffsetTop
    }

    fun reset() {
        lottieAnimationView.clearAnimation()
        lottieAnimationView.cancelAnimation()
        lottieAnimationView.visibility = GONE
        setColorViewAlpha(if (mAlpha) STARTING_PROGRESS_ALPHA else MAX_ALPHA)
        // Return the circle to its start position
        if (mScale) {
            setAnimationProgress(0f)
        } else {
            setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCurrentTargetOffsetTop)
        }
        mCurrentTargetOffsetTop = lottieAnimationView.top
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            reset()
        }
    }

    internal class SavedState : BaseSavedState {
        val mRefreshing: Boolean

        /**
         * Constructor called from [LottieSwipeRefreshLayout.onSaveInstanceState]
         */
        constructor(superState: Parcelable?, refreshing: Boolean) : super(superState) {
            mRefreshing = refreshing
        }

        /**
         * Constructor called from [.CREATOR]
         */
        constructor(`in`: Parcel) : super(`in`) {
            mRefreshing = `in`.readByte().toInt() != 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeByte(if (mRefreshing) 1.toByte() else 0.toByte())
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState?> = object : Parcelable.Creator<SavedState?> {
                override fun createFromParcel(`in`: Parcel): SavedState? {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return SavedState(superState, mRefreshing)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        isRefreshing = savedState.mRefreshing
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    private fun setColorViewAlpha(targetAlpha: Int) {
        lottieAnimationView.imageAlpha = targetAlpha
    }

    /**
     * The refresh indicator starting and resting position is always positioned
     * near the top of the refreshing content. This position is a consistent
     * location, but can be adjusted in either direction based on whether or not
     * there is a toolbar or actionbar present.
     *
     *
     * **Note:** Calling this will reset the position of the refresh indicator to
     * `start`.
     *
     *
     * @param scale Set to true if there is no view at a higher z-order than where the progress
     * spinner is set to appear. Setting it to true will cause indicator to be scaled
     * up rather than clipped.
     * @param start The offset in pixels from the top of this view at which the
     * progress spinner should appear.
     * @param end   The offset in pixels from the top of this view at which the
     * progress spinner should come to rest after a successful swipe
     * gesture.
     */
    fun setProgressViewOffset(scale: Boolean, start: Int, end: Int) {
        mScale = scale
        mOriginalOffsetTop = start
        mSpinnerOffsetEnd = end
        mUsingCustomStart = true
        reset()
        mRefreshing = false
    }

    /**
     * The refresh indicator resting position is always positioned near the top
     * of the refreshing content. This position is a consistent location, but
     * can be adjusted in either direction based on whether or not there is a
     * toolbar or actionbar present.
     *
     * @param scale Set to true if there is no view at a higher z-order than where the progress
     * spinner is set to appear. Setting it to true will cause indicator to be scaled
     * up rather than clipped.
     * @param end   The offset in pixels from the top of this view at which the
     * progress spinner should come to rest after a successful swipe
     * gesture.
     */
    fun setProgressViewEndTarget(scale: Boolean, end: Int) {
        mSpinnerOffsetEnd = end
        mScale = scale
        lottieAnimationView.invalidate()
    }

    /**
     * Sets the distance that the refresh indicator can be pulled beyond its resting position during
     * a swipe gesture. The default is [.DEFAULT_SLINGSHOT_DISTANCE].
     *
     * @param slingshotDistance The distance in pixels that the refresh indicator can be pulled
     * beyond its resting position.
     */
    fun setSlingshotDistance(@Px slingshotDistance: Int) {
        mCustomSlingshotDistance = slingshotDistance
    }

    fun setSizePx(@Px size: Int) {
        mCircleDiameter = size
    }

    fun setSmallSize() {
        setSizePx(dpToPx(CIRCLE_DIAMETER))
    }

    fun setLargeSize() {
        setSizePx(dpToPx(CIRCLE_DIAMETER_LARGE))
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        return if (lottieAnimationViewIndex < 0) {
            i
        } else if (i == childCount - 1) {
            // Draw the selected child last
            lottieAnimationViewIndex
        } else if (i >= lottieAnimationViewIndex) {
            // Move the children after the selected child earlier one
            i + 1
        } else {
            // Keep the children before the selected child the same
            i
        }
    }

    private fun createProgressView() {
        lottieAnimationView = createLottieView()
        lottieAnimationView.visibility = GONE
        addView(lottieAnimationView)
    }

    /**
     * Init Default LottieView. Override to custom it
     */
    protected fun createLottieView() = LottieAnimationView(context).apply {
        repeatCount = LottieDrawable.INFINITE
        speed = 2.0f
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    fun setOnRefreshListener(listener: OnRefreshListener?) {
        mListener = listener
    }

    open fun setOnRefreshListener(listener: () -> Unit) {
        mListener = object : OnRefreshListener {
            override fun onRefresh() {
                listener.invoke()
            }
        }
    }

    private fun startScaleUpAnimation(listener: AnimationListener) {
        lottieAnimationView.visibility = VISIBLE
        lottieAnimationView.imageAlpha = MAX_ALPHA
        val mScaleAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                setAnimationProgress(interpolatedTime)
            }
        }
        mScaleAnimation.duration = mMediumAnimationDuration.toLong()
        mScaleAnimation.setAnimationListener(listener)
        lottieAnimationView.clearAnimation()
        lottieAnimationView.startAnimation(mScaleAnimation)
    }

    /**
     * Pre API 11, this does an alpha animation.
     *
     * @param progress
     */
    fun setAnimationProgress(progress: Float) {
        lottieAnimationView.scaleX = progress
        lottieAnimationView.scaleY = progress
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (mRefreshing != refreshing) {
            mNotify = notify
            ensureTarget()
            mRefreshing = refreshing
            if (mRefreshing) {
                animateOffsetToCorrectPosition(mCurrentTargetOffsetTop, mRefreshListener)
            } else {
                animateOffsetToStartPosition(mCurrentTargetOffsetTop, mRefreshListener)
            }
        }
    }

    fun startScaleDownAnimation(listener: AnimationListener?) {
        val mScaleDownAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                setAnimationProgress(1 - interpolatedTime)
            }
        }
        mScaleDownAnimation.duration = SCALE_DOWN_DURATION.toLong()
        mScaleDownAnimation.setAnimationListener(listener)
        lottieAnimationView.clearAnimation()
        lottieAnimationView.startAnimation(mScaleDownAnimation)
    }

    private fun startProgressAlphaStartAnimation() {
        mAlphaStartAnimation = startAlphaAnimation(lottieAnimationView.imageAlpha, STARTING_PROGRESS_ALPHA)
    }

    private fun startProgressAlphaMaxAnimation() {
        mAlphaMaxAnimation = startAlphaAnimation(lottieAnimationView.imageAlpha, MAX_ALPHA)
    }

    private fun startAlphaAnimation(startingAlpha: Int, endingAlpha: Int): Animation {
        val alpha: Animation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                lottieAnimationView.imageAlpha = (startingAlpha + (endingAlpha - startingAlpha) * interpolatedTime).toInt()
            }
        }
        alpha.duration = ALPHA_ANIMATION_DURATION.toLong()
        // Clear out the previous animation listeners.
        lottieAnimationView.clearAnimation()
        lottieAnimationView.startAnimation(alpha)
        return alpha
    }

    fun setColorScheme(@ColorInt color: Int?) {
        if (color == null) return
        LottieHelper.setColorFilter(lottieAnimationView, color)
    }

    /**
     * Set tint color black in light theme, tint white in dark/night theme.
     */
    private fun enableAutoTintColor() {
        try {
            val color = if (resources.getBoolean(R.bool.dark_mode)) Color.WHITE else Color.BLACK
            setColorScheme(color)
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message.orEmpty())
        }
    }

    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     *
     * @param refreshing Whether or not the view should show refresh progress.
     */
    var isRefreshing: Boolean
        get() = mRefreshing
        set(refreshing) {
            if (refreshing && mRefreshing != refreshing) {
                // scale and show
                mRefreshing = refreshing
                var endTarget: Int = if (!mUsingCustomStart) {
                    mSpinnerOffsetEnd + mOriginalOffsetTop
                } else {
                    mSpinnerOffsetEnd
                }
                setTargetOffsetTopAndBottom(endTarget - mCurrentTargetOffsetTop)
                mNotify = false
                startScaleUpAnimation(mRefreshListener)
            } else {
                setRefreshing(refreshing, false /* notify */)
            }
        }

    private fun ensureTarget(): Boolean {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child != lottieAnimationView) {
                    mTarget = child
                    return true
                }
            }
        }
        return mTarget != null
    }

    /**
     * Set the distance to trigger a sync in dips
     *
     * @param distance
     */
    fun setDistanceToTriggerSync(distance: Int) {
        mTotalDragDistance = distance.toFloat()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = measuredWidth
        val height = measuredHeight
        if (childCount == 0) {
            return
        }
        if (ensureTarget().not()) {
            return
        }
        val child: View = mTarget!!
        val childLeft = paddingLeft
        val childTop: Int = if (shouldAnimateContent().not()) paddingTop else paddingTop + getCurrentOffset()
        val childWidth = width - paddingLeft - paddingRight
        val childHeight = height - paddingTop - paddingBottom
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        val circleWidth = lottieAnimationView.measuredWidth
        val circleHeight = lottieAnimationView.measuredHeight

        lottieAnimationView.layout(
            width / 2 - circleWidth / 2, mCurrentTargetOffsetTop,
            width / 2 + circleWidth / 2, mCurrentTargetOffsetTop + circleHeight
        )
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (ensureTarget().not()) {
            return
        }
        mTarget?.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth - paddingLeft - paddingRight, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight - paddingTop - paddingBottom, MeasureSpec.EXACTLY)
        )
        lottieAnimationView.measure(
            MeasureSpec.makeMeasureSpec(mCircleDiameter, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(mCircleDiameter, MeasureSpec.EXACTLY)
        )
        lottieAnimationViewIndex = -1
        // Get the index of the circleview.
        for (index in 0 until childCount) {
            if (getChildAt(index) === lottieAnimationView) {
                lottieAnimationViewIndex = index
                break
            }
        }
    }

    /**
     * Get the diameter of the progress circle that is displayed as part of the
     * swipe to refresh layout.
     *
     * @return Diameter in pixels of the progress circle view.
     */
    fun getProgressCircleDiameter(): Int {
        return mCircleDiameter
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    fun canChildScrollUp(): Boolean {
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback!!.canChildScrollUp(this, mTarget)
        }
        return if (mTarget is ListView) {
            ListViewCompat.canScrollList((mTarget as ListView), -1)
        } else mTarget?.canScrollVertically(-1) ?: false
    }

    /**
     * Set a callback to override [LottieSwipeRefreshLayout.canChildScrollUp] method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    fun setOnChildScrollUpCallback(callback: OnChildScrollUpCallback?) {
        mChildScrollUpCallback = callback
    }

    /**
     * Enables the legacy behavior of [.requestDisallowInterceptTouchEvent] from before
     * 1.1.0-alpha03, where the request is not propagated up to its parents in either of the
     * following two cases:
     *
     *  * The child as an [AbsListView] and the runtime is API < 21
     *  * The child has nested scrolling disabled
     *
     * Use this method *only* if your application:
     *
     *  * is upgrading SwipeRefreshLayout from &lt; 1.1.0-alpha03 to &gt;= 1.1.0-alpha03
     *  * relies on a parent of SwipeRefreshLayout to intercept touch events and that
     * parent no longer responds to touch events
     *  * setting this method to `true` fixes that issue
     *
     *
     * @param enabled `true` to enable the legacy behavior, `false` for default behavior
     */
    @Deprecated(
        """Only use this method if the changes introduced in
      {@link #requestDisallowInterceptTouchEvent} in version 1.1.0-alpha03 are breaking
      your application."""
    )
    fun setLegacyRequestDisallowInterceptTouchEventEnabled(enabled: Boolean) {
        mEnableLegacyRequestDisallowInterceptTouch = enabled
    }

    override fun requestDisallowInterceptTouchEvent(b: Boolean) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if (Build.VERSION.SDK_INT < 21 && mTarget is AbsListView
            || mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget!!)
        ) {
            if (mEnableLegacyRequestDisallowInterceptTouch) {
                // Nope.
            } else {
                // Ignore here, but pass it up to our parent
                val parent = parent
                parent?.requestDisallowInterceptTouchEvent(b)
            }
        } else {
            super.requestDisallowInterceptTouchEvent(b)
        }
    }

    // NestedScrollingParent 3
    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int, @ViewCompat.NestedScrollType type: Int,
        consumed: IntArray
    ) {
        if (type != ViewCompat.TYPE_TOUCH) {
            return
        }

        // This is a bit of a hack. onNestedScroll is typically called up the hierarchy of nested
        // scrolling parents/children, where each consumes distances before passing the remainder
        // to parents.  In our case, we want to try to run after children, and after parents, so we
        // first pass scroll distances to parents and consume after everything else has.
        val consumedBeforeParents = consumed[1]
        dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            mParentOffsetInWindow, type, consumed
        )
        val consumedByParents = consumed[1] - consumedBeforeParents
        val unconsumedAfterParents = dyUnconsumed - consumedByParents

        // There are two reasons why scroll distance may be totally consumed.  1) All of the nested
        // scrolling parents up the hierarchy implement NestedScrolling3 and consumed all of the
        // distance or 2) at least 1 nested scrolling parent doesn't implement NestedScrolling3 and
        // for comparability reasons, we are supposed to act like they have.
        //
        // We must assume 2) is the case because we have no way of determining that it isn't, and
        // therefore must fallback to a previous hack that was done before nested scrolling 3
        // existed.
        val remainingDistanceToScroll: Int = if (unconsumedAfterParents == 0) {
            // The previously implemented hack is to see how far we were offset and assume that that
            // distance is equal to how much all of our parents consumed.
            dyUnconsumed + mParentOffsetInWindow[1]
        } else {
            unconsumedAfterParents
        }

        // Not sure why we have to make sure the child can't scroll up... but seems dangerous to
        // remove.
        if (remainingDistanceToScroll < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(remainingDistanceToScroll).toFloat()
            moveSpinner(mTotalUnconsumed)

            // If we've gotten here, we need to consume whatever is left to consume, which at this
            // point is either equal to 0, or remainingDistanceToScroll.
            consumed[1] += unconsumedAfterParents
        }
    }

    // NestedScrollingParent 2
    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return if (type == ViewCompat.TYPE_TOUCH) {
            onStartNestedScroll(child, target, axes)
        } else {
            false
        }
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        // Should always be true because onStartNestedScroll returns false for all type !=
        // ViewCompat.TYPE_TOUCH, but check just in case.
        if (type == ViewCompat.TYPE_TOUCH) {
            onNestedScrollAccepted(child, target, axes)
        }
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        // Should always be true because onStartNestedScroll returns false for all type !=
        // ViewCompat.TYPE_TOUCH, but check just in case.
        if (type == ViewCompat.TYPE_TOUCH) {
            onStopNestedScroll(target)
        }
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, type: Int
    ) {
        onNestedScroll(
            target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type,
            mNestedScrollingV2ConsumedCompat
        )
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        // Should always be true because onStartNestedScroll returns false for all type !=
        // ViewCompat.TYPE_TOUCH, but check just in case.
        if (type == ViewCompat.TYPE_TOUCH) {
            onNestedPreScroll(target, dx, dy, consumed)
        }
    }

    // NestedScrollingParent 1
    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return (isEnabled && !mReturningToStart && !mRefreshing
                && nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0)
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
        // Dispatch up to the nested parent
        startNestedScroll(axes and ViewCompat.SCROLL_AXIS_VERTICAL)
        mTotalUnconsumed = 0f
        mNestedScrollInProgress = true
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = mTotalUnconsumed.toInt()
                mTotalUnconsumed = 0f
            } else {
                mTotalUnconsumed -= dy.toFloat()
                consumed[1] = dy
            }
        }

        // If a client layout is using a custom start position for the circle
        // view, they mean to hide it again before scrolling the child view
        // If we get back to mTotalUnconsumed == 0 and there is more to go, hide
        // the circle so it isn't exposed if its blocking content is moved
        if (mUsingCustomStart && dy > 0 && mTotalUnconsumed == 0f && Math.abs(dy - consumed[1]) > 0) {
            lottieAnimationView.visibility = GONE
        }

        // Now let our nested parent consume the leftovers
        val parentConsumed = mParentScrollConsumed
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0]
            consumed[1] += parentConsumed[1]
        }
    }

    override fun getNestedScrollAxes(): Int {
        return mNestedScrollingParentHelper.nestedScrollAxes
    }

    override fun onStopNestedScroll(target: View) {
        mNestedScrollingParentHelper.onStopNestedScroll(target)
        mNestedScrollInProgress = false
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        if (mTotalUnconsumed > 0) {
            finishSpinner(mTotalUnconsumed)
            mTotalUnconsumed = 0f
        }
        // Dispatch up our nested parent
        stopNestedScroll()
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int
    ) {
        onNestedScroll(
            target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            ViewCompat.TYPE_TOUCH, mNestedScrollingV2ConsumedCompat
        )
    }

    override fun onNestedPreFling(
        target: View, velocityX: Float,
        velocityY: Float
    ): Boolean {
        return dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun onNestedFling(
        target: View, velocityX: Float, velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return dispatchNestedFling(velocityX, velocityY, consumed)
    }

    // NestedScrollingChild 3
    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?, @ViewCompat.NestedScrollType type: Int,
        consumed: IntArray
    ) {
        if (type == ViewCompat.TYPE_TOUCH) {
            mNestedScrollingChildHelper.dispatchNestedScroll(
                dxConsumed, dyConsumed, dxUnconsumed,
                dyUnconsumed, offsetInWindow, type, consumed
            )
        }
    }

    // NestedScrollingChild 2
    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return type == ViewCompat.TYPE_TOUCH && startNestedScroll(axes)
    }

    override fun stopNestedScroll(type: Int) {
        if (type == ViewCompat.TYPE_TOUCH) {
            stopNestedScroll()
        }
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return type == ViewCompat.TYPE_TOUCH && hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return type == ViewCompat.TYPE_TOUCH && mNestedScrollingChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return type == ViewCompat.TYPE_TOUCH && dispatchNestedPreScroll(
            dx, dy, consumed,
            offsetInWindow
        )
    }

    // NestedScrollingChild 1
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mNestedScrollingChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return mNestedScrollingChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return mNestedScrollingChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed,
            dxUnconsumed, dyUnconsumed, offsetInWindow
        )
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
            dx, dy, consumed, offsetInWindow
        )
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    private fun isAnimationRunning(animation: Animation?): Boolean {
        return animation != null && animation.hasStarted() && !animation.hasEnded()
    }

    private fun moveSpinner(overscrollTop: Float) {
        val originalDragPercent = overscrollTop / mTotalDragDistance
        val dragPercent = Math.min(1f, Math.abs(originalDragPercent))
        val extraOS = Math.abs(overscrollTop) - mTotalDragDistance
        val slingshotDist = if (mCustomSlingshotDistance > 0) {
            mCustomSlingshotDistance.toFloat()
        } else {
            (if (mUsingCustomStart) mSpinnerOffsetEnd - mOriginalOffsetTop else mSpinnerOffsetEnd).toFloat()
        }
        val tensionSlingshotPercent = Math.max(0f, Math.min(extraOS, slingshotDist * 2) / slingshotDist)
        val tensionPercent = (tensionSlingshotPercent / 4 - Math.pow((tensionSlingshotPercent / 4).toDouble(), 2.0)).toFloat() * 2f
        val extraMove = slingshotDist * tensionPercent * 2
        val targetY = mOriginalOffsetTop + (slingshotDist * dragPercent + extraMove).toInt()
        // where 1.0f is a full circle
        if (lottieAnimationView.visibility != VISIBLE) {
            lottieAnimationView.visibility = VISIBLE
        }
        if (!mScale) {
            lottieAnimationView.scaleX = 1f
            lottieAnimationView.scaleY = 1f
        }
        if (mScale) {
            setAnimationProgress(Math.min(1f, overscrollTop / mTotalDragDistance))
        }
        if (overscrollTop < mTotalDragDistance) {
            if (mAlpha && lottieAnimationView.imageAlpha > STARTING_PROGRESS_ALPHA && isAnimationRunning(mAlphaStartAnimation).not()) {
                // Animate the alpha
                startProgressAlphaStartAnimation()
            }
        } else {
            if (mAlpha && lottieAnimationView.imageAlpha <= MAX_ALPHA && !isAnimationRunning(mAlphaMaxAnimation)) {
                // Animate the alpha
                startProgressAlphaMaxAnimation()
            }
        }
        setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop)
    }

    private fun finishSpinner(overscrollTop: Float) {
        if (overscrollTop > mTotalDragDistance) {
            setRefreshing(true, true /* notify */)
        } else {
            // cancel refresh
            mRefreshing = false
            var listener: AnimationListener? = null
            if (!mScale) {
                listener = object : AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationEnd(animation: Animation) {
                        if (!mScale) {
                            startScaleDownAnimation(null)
                        }
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                }
            }
            animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        ensureTarget()
        val action = ev.actionMasked
        val pointerIndex: Int
        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false
        }
        if (!isEnabled || mReturningToStart || canChildScrollUp() || mRefreshing || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                setTargetOffsetTopAndBottom(mOriginalOffsetTop - lottieAnimationView.top)
                mActivePointerId = ev.getPointerId(0)
                mIsBeingDragged = false
                pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) {
                    return false
                }
                mInitialDownY = ev.getY(pointerIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) {
                    return false
                }
                val y = ev.getY(pointerIndex)
                startDragging(y)
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
            }
        }
        return mIsBeingDragged
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        var pointerIndex = -1
        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false
        }
        if (!isEnabled || mReturningToStart || canChildScrollUp() || mRefreshing || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mActivePointerId = ev.getPointerId(0)
                mIsBeingDragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.")
                    return false
                }
                val y = ev.getY(pointerIndex)
                startDragging(y)
                if (mIsBeingDragged) {
                    val overscrollTop = (y - mInitialMotionY) * DRAG_RATE
                    if (overscrollTop > 0) {
                        // While the spinner is being dragged down, our parent shouldn't try
                        // to intercept touch events. It will stop the drag gesture abruptly.
                        parent.requestDisallowInterceptTouchEvent(true)
                        moveSpinner(overscrollTop)
                    } else {
                        return false
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerIndex = ev.actionIndex
                if (pointerIndex < 0) {
                    Log.e(
                        LOG_TAG,
                        "Got ACTION_POINTER_DOWN event but have an invalid action index."
                    )
                    return false
                }
                mActivePointerId = ev.getPointerId(pointerIndex)
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP -> {
                pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.")
                    return false
                }
                if (mIsBeingDragged) {
                    val y = ev.getY(pointerIndex)
                    val overscrollTop = (y - mInitialMotionY) * DRAG_RATE
                    mIsBeingDragged = false
                    finishSpinner(overscrollTop)
                }
                mActivePointerId = INVALID_POINTER
                return false
            }
            MotionEvent.ACTION_CANCEL -> return false
        }
        return true
    }

    private fun startDragging(y: Float) {
        val yDiff = y - mInitialDownY
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
            mInitialMotionY = mInitialDownY + mTouchSlop
            mIsBeingDragged = true
            lottieAnimationView.imageAlpha = STARTING_PROGRESS_ALPHA
        }
    }

    private fun animateOffsetToCorrectPosition(from: Int, listener: AnimationListener?) {
        mFrom = from
        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = ANIMATE_TO_TRIGGER_DURATION.toLong()
        mAnimateToCorrectPosition.interpolator = mDecelerateInterpolator
        if (listener != null) {
            mAnimateToCorrectPosition.setAnimationListener(listener)
        }
        lottieAnimationView.clearAnimation()
        lottieAnimationView.startAnimation(mAnimateToCorrectPosition)
    }

    private fun animateOffsetToStartPosition(from: Int, listener: AnimationListener?) {
        if (mScale) {
            // Scale the item back down
            startScaleDownReturnToStartAnimation(from, listener)
        } else {
            mFrom = from
            mAnimateToStartPosition.reset()
            mAnimateToStartPosition.duration = ANIMATE_TO_START_DURATION.toLong()
            mAnimateToStartPosition.interpolator = mDecelerateInterpolator
            if (listener != null) {
                mAnimateToStartPosition.setAnimationListener(listener)
            }
            lottieAnimationView.clearAnimation()
            lottieAnimationView.startAnimation(mAnimateToStartPosition)
        }
    }

    private val mAnimateToCorrectPosition: Animation = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val endTarget: Int = if (!mUsingCustomStart) {
                mSpinnerOffsetEnd - abs(mOriginalOffsetTop)
            } else {
                mSpinnerOffsetEnd
            }
            val targetTop = mFrom + ((endTarget - mFrom) * interpolatedTime).toInt()
            val offset = targetTop - lottieAnimationView.top
            setTargetOffsetTopAndBottom(offset)
        }
    }

    fun moveToStart(interpolatedTime: Float) {
        val targetTop = mFrom + ((mOriginalOffsetTop - mFrom) * interpolatedTime).toInt()
        val offset = targetTop - lottieAnimationView.top
        setTargetOffsetTopAndBottom(offset)
    }

    private val mAnimateToStartPosition: Animation = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToStart(interpolatedTime)
        }
    }

    private fun startScaleDownReturnToStartAnimation(
        from: Int,
        listener: AnimationListener?
    ) {
        mFrom = from
        mStartingScale = lottieAnimationView.scaleX
        val mScaleDownToStartAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                val targetScale = mStartingScale + -mStartingScale * interpolatedTime
                setAnimationProgress(targetScale)
                moveToStart(interpolatedTime)
            }
        }
        mScaleDownToStartAnimation.duration = SCALE_DOWN_DURATION.toLong()
        if (listener != null) {
            mScaleDownToStartAnimation.setAnimationListener(listener)
        }
        lottieAnimationView.clearAnimation()
        lottieAnimationView.startAnimation(mScaleDownToStartAnimation)
    }

    fun setTargetOffsetTopAndBottom(offset: Int) {
        lottieAnimationView.bringToFront()
        ViewCompat.offsetTopAndBottom(lottieAnimationView, offset)
        mCurrentTargetOffsetTop = lottieAnimationView.top
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = ev.getPointerId(newPointerIndex)
        }
    }

    open fun getProgressViewStartOffset(): Int {
        return mOriginalOffsetTop
    }

    open fun getProgressViewEndOffset(): Int {
        return mSpinnerOffsetEnd
    }

    private fun shouldAnimateContent(): Boolean = indicatorOverlay.not() && ensureTarget()

    private fun getCurrentOffset() = mCurrentTargetOffsetTop - mOriginalOffsetTop

    private fun dpToPx(dp: Int): Int {
        val metrics = resources.displayMetrics
        return (dp * metrics.density).toInt()
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a refresh should implement this interface.
     */
    interface OnRefreshListener {
        /**
         * Called when a swipe gesture triggers a refresh.
         */
        fun onRefresh()
    }

    /**
     * Classes that wish to override [LottieSwipeRefreshLayout.canChildScrollUp] method
     * behavior should implement this interface.
     */
    interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when [LottieSwipeRefreshLayout.canChildScrollUp] method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SwipeRefreshLayout that this callback is overriding.
         * @param child  The child view of SwipeRefreshLayout.
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        fun canChildScrollUp(parent: LottieSwipeRefreshLayout, child: View?): Boolean
    }

    companion object {

        private val LOG_TAG = LottieSwipeRefreshLayout::class.java.simpleName

        private const val CIRCLE_DIAMETER = 40
        private const val CIRCLE_DIAMETER_LARGE = 56

        // Spacing between lottie and top/bottom
        private const val INDICATOR_PADDING_TOP = 12
        private const val INDICATOR_PADDING_BOTTOM = 12

        private const val MAX_ALPHA = 255
        private const val STARTING_PROGRESS_ALPHA = (.3f * MAX_ALPHA).toInt()
        private const val DECELERATE_INTERPOLATION_FACTOR = 2f
        private const val INVALID_POINTER = -1
        private const val DRAG_RATE = .5f

        // Max amount of circle that can be filled by progress during swipe gesture,
        // where 1.0 is a full circle
        private const val SCALE_DOWN_DURATION = 250
        private const val ALPHA_ANIMATION_DURATION = 300
        private const val ANIMATE_TO_TRIGGER_DURATION = 200
        private const val ANIMATE_TO_START_DURATION = 250
    }
}