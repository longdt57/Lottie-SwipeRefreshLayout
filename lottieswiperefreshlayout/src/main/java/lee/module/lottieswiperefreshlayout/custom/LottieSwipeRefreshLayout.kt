package lee.module.lottieswiperefreshlayout.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.ListView
import androidx.annotation.DimenRes
import androidx.core.view.*
import androidx.core.widget.ListViewCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import lee.module.lottieswiperefreshlayout.R
import lee.module.lottieswiperefreshlayout.custom.LottieSwipeRefreshLayout.*

/**
 * This library took its original inspiration from:
 * @see [link](https://github.com/nabil6391/LottieSwipeRefreshLayout)
 *
 * @see [androidx.swiperefreshlayout.widget.SwipeRefreshLayout]
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
 * <p>
 * This layout should be made the parent of the view that will be refreshed as a
 * result of the gesture and can only support one direct child. This view will
 * also be made the target of the gesture and will be forced to match both the
 * width and the height supplied in this layout. The SwipeRefreshLayout does not
 * provide accessibility events; instead, a menu item must be provided to allow
 * refresh of the content wherever this gesture is used.
 * </p>
 *
 * How to customize Lottie
 * [lottieAnimationView] reference to lottie: update layoutParam, speed...
 *
 * [setSize] - Change Lottie Size
 * [createLottieView] - Override to create a custom lottie view
 * [setLottieView] - Set a custom lottie view
 */
open class LottieSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    ViewGroup(context, attrs, defStyle), NestedScrollingParent3,
    NestedScrollingParent2, NestedScrollingChild3, NestedScrollingChild2,
    NestedScrollingParent,
    NestedScrollingChild {

    private lateinit var mTarget: View // ContentView

    /**
     * Refer:
     * - [androidx.swiperefreshlayout.widget.SwipeRefreshLayout.mCircleView]
     * - [androidx.swiperefreshlayout.widget.SwipeRefreshLayout.mProgress]
     */
    var lottieAnimationView: LottieAnimationView // mCircleView
        private set

    private var lottiePosAttr: PositionAttr = PositionAttr()
    private var contentPosAttr: PositionAttr = PositionAttr()

    private var notify: Boolean = true
    var isRefreshing: Boolean = false
        set(refreshing) {
            if (isRefreshing != refreshing) {
                field = refreshing
                if (refreshing) {
                    if (currentState != State.TRIGGERING) {
                        startRefreshing()
                    }
                } else {
                    notify = false
                    currentState = State.ROLLING
                    stopRefreshing()
                }
            }
        }

    /**
     * @param triggerOffSetTop : The offset in pixels from the top of this view at which the progress indicator should
     *         come to rest after a successful swipe gesture.
     */
    var triggerOffSetTop = 0
        private set

    /**
     * @param maxOffSetTop : The maximum distance in pixels that the refresh indicator can be pulled
     *        beyond its resting position.
     */
    var maxOffSetTop = 0
        private set

    /**
     * @param overlay Whether to overlay the indicator on top of the content or not
     */
    var overlay = true
        private set

    private var downX = 0F
    private var downY = 0F

    private var offsetY = 0F
    private var lastPullFraction = 0F

    private var currentState: State = State.IDLE

    private val onProgressListeners: MutableCollection<(Float) -> Unit> = mutableListOf()
    private val onTriggerListeners: MutableCollection<() -> Unit> = mutableListOf()

    private val mNestedScrollingParentHelper: NestedScrollingParentHelper by lazy { NestedScrollingParentHelper(this) }
    private val mNestedScrollingChildHelper: NestedScrollingChildHelper by lazy { NestedScrollingChildHelper(this) }
    private val mParentScrollConsumed = IntArray(2)
    private val mParentOffsetInWindow = IntArray(2)
    private var mNestedScrollInProgress = false

    private var mTotalUnconsumed = 0f
    private val mNestedScrollingV2ConsumedCompat = IntArray(2)

    private var mChildScrollUpCallback: ((LottieSwipeRefreshLayout, View?) -> Boolean)? = null

    /**
     * @see .setLegacyRequestDisallowInterceptTouchEventEnabled
     */
    private var mEnableLegacyRequestDisallowInterceptTouch = false


    init {
        val style = context.theme.obtainStyledAttributes(attrs, R.styleable.LottieSwipeRefreshLayout, defStyle, 0)

        // Lottie
        lottieAnimationView = createLottieView()
        this.addView(lottieAnimationView)

        try {
            initLottie(style)
            initOffset(style)
            overlay = style.getBoolean(R.styleable.LottieSwipeRefreshLayout_indicator_overlay, overlay)
        } finally {
            style.recycle()
        }

        isNestedScrollingEnabled = true
    }

    // Offset
    private fun initOffset(style: TypedArray) {
        val defaultLottiSize = resources.getDimensionPixelOffset(R.dimen.lottie_size_default)
        val defaultOffsetTop = style.getDimensionPixelOffset(R.styleable.LottieSwipeRefreshLayout_lottie_srl_size, defaultLottiSize)

        triggerOffSetTop = style.getDimensionPixelOffset(R.styleable.LottieSwipeRefreshLayout_trigger_offset_top, defaultOffsetTop)
        maxOffSetTop = style.getDimensionPixelOffset(R.styleable.LottieSwipeRefreshLayout_max_offset_top, defaultOffsetTop * 2)

        if (maxOffSetTop <= triggerOffSetTop) {
            maxOffSetTop = triggerOffSetTop * 2
        }
    }

    // Lottie
    private fun initLottie(style: TypedArray) {

        val lottieRawRes = style.getResourceId(R.styleable.LottieSwipeRefreshLayout_lottie_srl_rawRes, R.raw.loader_zm)
        lottieAnimationView.setAnimation(lottieRawRes)

        val lottieSizeRes = style.getResourceId(R.styleable.LottieSwipeRefreshLayout_lottie_srl_size, R.dimen.lottie_size_default)
        setSize(lottieSizeRes)

        addProgressListener {
            lottieAnimationView.progress = it
        }

        addTriggerListener {
            lottieAnimationView.resumeAnimation()
        }
    }

    /**
     * Map contentView
     */
    private fun ensureTarget() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child != lottieAnimationView) {
                mTarget = child
                break
            }
        }

        if (::mTarget.isInitialized.not()) {
            throw IllegalStateException("$LOG_TAG - There is no content view")
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // Should have only 1 container
        if (childCount != 2) {
            throw IllegalStateException("$LOG_TAG - Only a topView and a contentView are allowed. Exactly 2 children are expected, but was $childCount")
        }
        ensureTarget()
    }

    private fun reset() {
        lottieAnimationView.clearAnimation()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            reset()
        }
    }

    /**
     * Refer: [androidx.swiperefreshlayout.widget.SwipeRefreshLayout.SavedState]
     */
    class SavedState : BaseSavedState {
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
            val CREATOR: Parcelable.Creator<SavedState?> =
                object : Parcelable.Creator<SavedState?> {
                    override fun createFromParcel(`in`: Parcel): SavedState {
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
        return SavedState(superState, isRefreshing)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        fun measureChild(view: View, widthMeasureSpec: Int, heightMeasureSpec: Int) {
            measureChildWithMargins(view, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }

        fun setInitialValues() {
            val topView = lottieAnimationView
            val layoutParams = topView.layoutParams as MarginLayoutParams
            val topViewHeight =
                topView.measuredHeight + layoutParams.topMargin + layoutParams.bottomMargin
            lottiePosAttr = lottiePosAttr.copy(height = topViewHeight)
        }

        measureChild(lottieAnimationView, widthMeasureSpec, heightMeasureSpec)
        measureChild(mTarget, widthMeasureSpec, heightMeasureSpec)

        setInitialValues()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        layoutTopView()
        layoutContentView()
    }

    private fun layoutTopView() {
        val topView = lottieAnimationView
        val topViewAttr = lottiePosAttr

        val lp = topView.layoutParams as MarginLayoutParams
        val top: Int = (paddingTop + lp.topMargin) - topViewAttr.height * 2 / 3 - ELEVATION
        val bottom = -ELEVATION
        if (lp.width == LayoutParams.MATCH_PARENT) {
            val left: Int = paddingLeft + lp.leftMargin
            val right: Int = left + topView.measuredWidth

            lottiePosAttr = PositionAttr(left = left, top = top, right = right, bottom = bottom)
            topView.layout(left, top, right, bottom)
        } else {
            val indicatorWidth: Int = topView.measuredWidth
            val left: Int = width / 2 - indicatorWidth / 2
            val right: Int = width / 2 + indicatorWidth / 2

            lottiePosAttr = PositionAttr(
                left = left,
                top = top,
                right = right,
                bottom = bottom
            )
            topView.layout(left, top, right, bottom)
        }

    }

    private fun layoutContentView() {
        val contentView = mTarget

        val lp = contentView.layoutParams as MarginLayoutParams
        val left: Int = paddingLeft + lp.leftMargin
        val top: Int = paddingTop + lp.topMargin
        val right: Int = left + contentView.measuredWidth
        val bottom: Int = top + contentView.measuredHeight

        contentPosAttr = PositionAttr(
                left = left,
                top = top,
                right = right,
                bottom = bottom
        )
        contentView.layout(left, top, right, bottom)
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    open fun canChildScrollUp(): Boolean {
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback!!.invoke(this, mTarget)
        }

        return if (mTarget is ListView) {
            ListViewCompat.canScrollList((mTarget as ListView?)!!, -1)
        } else mTarget.canScrollVertically(-1)
    }

    /**
     * Set a callback to override [LottieSwipeRefreshLayout.canChildScrollUp] method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    open fun setOnChildScrollUpCallback(callback: ((LottieSwipeRefreshLayout, View?) -> Boolean)?) {
        mChildScrollUpCallback = callback
    }

    //Lottie Customize

    /**
     * Init Default LottieView. Override to custom it
     */
    protected fun createLottieView() = LottieAnimationView(context).apply {
        repeatCount = LottieDrawable.INFINITE

        val density = context.resources.displayMetrics.density
        ViewCompat.setElevation(this, ELEVATION * density)
        speed = 2.0f
    }

    fun setLottieView(lottieView: LottieAnimationView) {
        lottieAnimationView = lottieView
    }

    // Lottie Helper

    fun setSize(@DimenRes sizeRes: Int) {
        val lottiSize = resources.getDimensionPixelSize(sizeRes)
        lottieAnimationView.layoutParams = MarginLayoutParams(lottiSize, lottiSize)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || isRefreshing || currentState == State.ROLLING || mNestedScrollInProgress || canChildScrollUp()) {
            return false
        }

        fun checkIfScrolledFurther(ev: MotionEvent, dy: Float, dx: Float) =
            if (!mTarget.canScrollVertically(-1)) {
                ev.y > downY && Math.abs(dy) > Math.abs(dx)
            } else {
                false
            }

        var shouldStealTouchEvents = false

        if (currentState != State.IDLE) {
            shouldStealTouchEvents = false
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                shouldStealTouchEvents = checkIfScrolledFurther(ev, dy, dx)
            }
        }

        return shouldStealTouchEvents
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || isRefreshing || currentState == State.ROLLING || mNestedScrollInProgress || canChildScrollUp()) {
            return false
        }

        var handledTouchEvent = true

        if (currentState != State.IDLE) {
            handledTouchEvent = false
        }

        parent.requestDisallowInterceptTouchEvent(true)
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                offsetY = (event.y - downY) * (1 - STICKY_FACTOR * STICKY_MULTIPLIER)
                notify = true
                move()
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP,
            -> {
                currentState = State.ROLLING
                stopRefreshing()
            }
        }

        return handledTouchEvent
    }

    open fun startRefreshing() {
        val triggerOffset: Float = if (offsetY > triggerOffSetTop) offsetY else triggerOffSetTop.toFloat()

        ValueAnimator.ofFloat(0F, 1F).apply {
            duration = ROLL_BACK_DURATION
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                positionChildren(triggerOffset * animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    offsetY = triggerOffset.toFloat()
                }
            })
            start()
        }
        lottieAnimationView.resumeAnimation()
    }

    private fun move() {
        val pullFraction: Float =
            if (offsetY == 0F) 0F else if (triggerOffSetTop > offsetY) offsetY / triggerOffSetTop else 1F
        offsetY = offsetY.coerceIn(0f, maxOffSetTop.toFloat())

        onProgressListeners.forEach { it(pullFraction) }
        lastPullFraction = pullFraction

        positionChildren(offsetY)
    }

    open fun stopRefreshing() {
        val rollBackOffset = if (offsetY > triggerOffSetTop) offsetY - triggerOffSetTop else offsetY
        val triggerOffset = if (rollBackOffset != offsetY) triggerOffSetTop else 0

        ValueAnimator.ofFloat(1F, 0F).apply {
            duration = ROLL_BACK_DURATION
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                positionChildren(triggerOffset + rollBackOffset * animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    if (notify && triggerOffset != 0 && currentState == State.ROLLING) {
                        currentState = State.TRIGGERING
                        isRefreshing = true
                        offsetY = triggerOffset.toFloat()
                        onTriggerListeners.forEach { it() }
                    } else {
                        currentState = State.IDLE
                        offsetY = 0f
                    }
                }
            })
            start()
        }
        lottieAnimationView.pauseAnimation()
    }

    private fun positionChildren(offset: Float) {
        lottieAnimationView.bringToFront()
        lottieAnimationView.y = lottiePosAttr.top + offset

        if (!overlay) {
            mTarget.y = contentPosAttr.top + offset
        }
    }

    //<editor-fold desc="Helpers">
    fun addProgressListener(onProgressListener: (Float) -> Unit) {
        onProgressListeners.add(onProgressListener)
    }

    open fun setLegacyRequestDisallowInterceptTouchEventEnabled(enabled: Boolean) {
        mEnableLegacyRequestDisallowInterceptTouch = enabled
    }

    /**
     * Enables the legacy behavior of {@link #requestDisallowInterceptTouchEvent} from before
     * 1.1.0-alpha03, where the request is not propagated up to its parents in either of the
     * following two cases:
     * <ul>
     *     <li>The child as an {@link AbsListView} and the runtime is API < 21</li>
     *     <li>The child has nested scrolling disabled</li>
     * </ul>
     * Use this method <em>only</em> if your application:
     * <ul>
     *     <li>is upgrading SwipeRefreshLayout from &lt; 1.1.0-alpha03 to &gt;= 1.1.0-alpha03</li>
     *     <li>relies on a parent of SwipeRefreshLayout to intercept touch events and that
     *     parent no longer responds to touch events</li>
     *     <li>setting this method to {@code true} fixes that issue</li>
     * </ul>
     *
     * @param enabled {@code true} to enable the legacy behavior, {@code false} for default behavior
     * @deprecated Only use this method if the changes introduced in
     * {@link #requestDisallowInterceptTouchEvent} in version 1.1.0-alpha03 are breaking
     * your application.
     */
    override fun requestDisallowInterceptTouchEvent(b: Boolean) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if (Build.VERSION.SDK_INT < 21 && mTarget is AbsListView
            || mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget)
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

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    open fun setOnRefreshListener(listener: () -> Unit) {
        onTriggerListeners.add(listener)
    }

    private fun addTriggerListener(onTriggerListener: () -> Unit) {
        onTriggerListeners.add(onTriggerListener)
    }

    override fun checkLayoutParams(p: LayoutParams?) = null != p && p is MarginLayoutParams

    override fun generateDefaultLayoutParams() =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?) = MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?) = MarginLayoutParams(p)

    enum class State {
        IDLE,
        ROLLING,
        TRIGGERING
    }

    data class PositionAttr(
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0,
        val height: Int = 0
    )

    /**
     * The behavior should be the same with SwipeRefreshLayout
     * @see [androidx.swiperefreshlayout.widget.SwipeRefreshLayout.onNestedScroll]
     */

    // NestedScrollingParent 3

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        if (type != ViewCompat.TYPE_TOUCH) {
            return
        }

        // This is a bit of a hack. onNestedScroll is typically called up the hierarchy of nested
        // scrolling parents/children, where each consumes distances before passing the remainder
        // to parents.  In our case, we want to try to run after children, and after parents, so we
        // first pass scroll distances to parents and consume after everything else has.

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

        // There are two reasons why scroll distance may be totally consumed.  1) All of the nested
        // scrolling parents up the hierarchy implement NestedScrolling3 and consumed all of the
        // distance or 2) at least 1 nested scrolling parent doesn't implement NestedScrolling3 and
        // for comparability reasons, we are supposed to act like they have.
        //
        // We must assume 2) is the case because we have no way of determining that it isn't, and
        // therefore must fallback to a previous hack that was done before nested scrolling 3
        // existed.
        val remainingDistanceToScroll: Int
        remainingDistanceToScroll = if (unconsumedAfterParents == 0) {
            // The previously implemented hack is to see how far we were offset and assume that that
            // distance is equal to how much all of our parents consumed.
            dyUnconsumed + mParentOffsetInWindow[1]
        } else {
            unconsumedAfterParents
        }

        // Not sure why we have to make sure the child can't scroll up... but seems dangerous to
        // remove.

        // Not sure why we have to make sure the child can't scroll up... but seems dangerous to
        // remove.
        if (remainingDistanceToScroll < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(remainingDistanceToScroll).toFloat()
//            moveSpinner(mTotalUnconsumed)

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
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
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

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return (isEnabled && currentState != State.ROLLING && !isRefreshing
                && nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0)
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
        // Dispatch up to the nested parent
        startNestedScroll(axes and ViewCompat.SCROLL_AXIS_VERTICAL)
        offsetY = 0f
        mNestedScrollInProgress = true
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll

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

        // If a client layout is using a custom start position for the circle
        // view, they mean to hide it again before scrolling the child view
        // If we get back to mTotalUnconsumed == 0 and there is more to go, hide
        // the circle so it isn't exposed if its blocking content is moved

        // Now let our nested parent consume the leftovers

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
        // Finish the Indicator for nested scrolling if we ever consumed any unconsumed nested scroll
        if (offsetY > 0) {
            notify = true
            currentState = State.ROLLING
            stopRefreshing()
            offsetY = 0f
        }
        // Dispatch up our nested parent
        stopNestedScroll()
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
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
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
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
        mNestedScrollingChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return mNestedScrollingChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return type == ViewCompat.TYPE_TOUCH && mNestedScrollingChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
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
        dyUnconsumed: Int, offsetInWindow: IntArray?,
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed,
            dxUnconsumed, dyUnconsumed, offsetInWindow
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
            dx, dy, consumed, offsetInWindow
        )
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    companion object {
        private const val LOG_TAG = "LottieSwipeRefreshLayout"

        private const val STICKY_FACTOR = 0.66F
        private const val STICKY_MULTIPLIER = 0.75F
        private const val ROLL_BACK_DURATION = 300L
        private const val ELEVATION = 4
    }

}
