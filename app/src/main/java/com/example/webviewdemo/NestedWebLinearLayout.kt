package com.example.webviewdemo

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.OverScroller
import androidx.core.view.*
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/2/26
 */
class NestedWebLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr), NestedScrollingParent3, NestedScrollingChild3 {

    private val webView: MyWebView by lazy {
        findViewById(R.id.web_view)
    }

    private val commentTitleLayout: View by lazy {
        findViewById(R.id.comment_title_layout)
    }

    private val commentRcv: RecyclerView by lazy {
        findViewById(R.id.comment_rcv)
    }

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop: Int
    private val maximumFlingVelocity: Int
    private val minimumFlingVelocity: Int

    private var lastMotionY: Float = 0f
    private var isBeingDragged: Boolean = false
    private var lastScrollerY: Int = 0

    private val parentHelper = NestedScrollingParentHelper(this)
    private val childHelper = NestedScrollingChildHelper(this)

    /**
     * 嵌套滑动使用的一个输出参数, 用来记录嵌套滑动被 parent 消费的距离
     */
    private val scrollConsumed = IntArray(2)

    init {
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        maximumFlingVelocity = configuration.scaledMaximumFlingVelocity
        minimumFlingVelocity = configuration.scaledMinimumFlingVelocity
        //开启嵌套滑动支持
        isNestedScrollingEnabled = true
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var layoutWidth = 0
        var layoutHeight = 0

        //测量 webView
        measureChildWithMargins(webView, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val webViewParams = webView.layoutParams as MarginLayoutParams
        val webViewUsageWidth = webView.measuredWidth + webViewParams.leftMargin + webViewParams.rightMargin
        val webViewUsageHeight = webView.measuredHeight + webViewParams.topMargin + webViewParams.bottomMargin

        layoutWidth = max(layoutWidth, webViewUsageWidth)
        layoutHeight += webViewUsageHeight

        //测量 titleLayout
        measureChildWithMargins(commentTitleLayout, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val titleParams = commentTitleLayout.layoutParams as MarginLayoutParams
        val titleUsageWidth = commentTitleLayout.measuredWidth + titleParams.leftMargin + titleParams.rightMargin
        val titleUsageHeight = commentTitleLayout.measuredHeight + titleParams.topMargin + titleParams.bottomMargin

        layoutWidth = max(layoutWidth, titleUsageWidth)
        layoutHeight += titleUsageHeight

        //测量 commentRcv, commentRcv 的高度为 parent 的高度 - title 的高度, 因为 title 是固定的
        measureChildWithMargins(commentRcv, widthMeasureSpec, 0, heightMeasureSpec, titleUsageHeight)
        val rcvParams = commentRcv.layoutParams as MarginLayoutParams
        val rcvUsageWidth = commentRcv.measuredWidth + rcvParams.leftMargin + rcvParams.rightMargin
        val rcvUsageHeight = commentRcv.measuredHeight + rcvParams.topMargin + rcvParams.bottomMargin

        layoutWidth = max(layoutWidth, rcvUsageWidth)
        layoutHeight += rcvUsageHeight

        val measureWidth =
            if (widthMode == MeasureSpec.EXACTLY) {
                widthSize
            } else {
                layoutWidth + paddingLeft + paddingRight
            }
        val measureHeight =
            if (heightMode == MeasureSpec.EXACTLY) {
                heightSize
            } else {
                layoutHeight + paddingTop + paddingBottom
            }
        setMeasuredDimension(measureWidth, measureHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0

        val webViewParams = webView.layoutParams as MarginLayoutParams
        left = paddingLeft + webViewParams.leftMargin
        top = paddingTop + webViewParams.topMargin
        right = left + webView.measuredWidth
        bottom = top + webView.measuredHeight
        webView.layout(left, top, right, bottom)

        val titleParams = commentTitleLayout.layoutParams as MarginLayoutParams
        left = paddingLeft + titleParams.leftMargin
        top = bottom + titleParams.topMargin
        right = left + commentTitleLayout.measuredWidth
        bottom = top + commentTitleLayout.measuredHeight
        commentTitleLayout.layout(left, top, right, bottom)

        val rcvParams = commentRcv.layoutParams as MarginLayoutParams
        left = paddingLeft + rcvParams.leftMargin
        top = bottom + rcvParams.topMargin
        right = left + commentRcv.measuredWidth
        bottom = top + commentRcv.measuredHeight
        commentRcv.layout(left, top, right, bottom)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    private fun initOrResetVelocityTracker() {
        velocityTracker?.clear() ?: kotlin.run {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    private fun initVelocityTrackerIfNotExists() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.let {
            it.recycle()
            velocityTracker = null
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isBeingDragged && event.action == MotionEvent.ACTION_MOVE) {
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastMotionY = event.y

                //初始化或者重置速度追踪器
                initOrResetVelocityTracker()
                velocityTracker?.addMovement(event)

                //先调用 computeScrollOffset 是为了让 isFinished 返回正确的结果
                scroller.computeScrollOffset()
                isBeingDragged = !scroller.isFinished

                //开始嵌套滑动 - TYPE_TOUCH
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastMotionY
                if (abs(dy) > touchSlop) {
                    lastMotionY = event.y
                    isBeingDragged = true

                    //尝试初始化速度追踪器
                    initVelocityTrackerIfNotExists()
                    velocityTracker?.addMovement(event)

                    //已经在滑动了, 就禁用 parent 的事件拦截
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                //尝试回收速度追踪器
                recycleVelocityTracker()
                //停止嵌套滑动 - TYPE_TOUCH
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
        }
        return isBeingDragged
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        //尝试初始化速度追踪器
        initVelocityTrackerIfNotExists()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isBeingDragged = !scroller.isFinished
                lastMotionY = event.y

                //已经在滑动了, 就禁用 parent 的事件拦截
                if (isBeingDragged) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                //如果上一次滚动没有结束, 那就结束它
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }

                //开始嵌套滑动 - TYPE_TOUCH
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = (lastMotionY - event.y).toInt()
                if (!isBeingDragged && abs(deltaY) > touchSlop) {
                    isBeingDragged = true
                    //已经在滑动了, 就禁用 parent 的事件拦截
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                //执行滑动操作
                if (isBeingDragged) {
                    lastMotionY = event.y

                    //先把滑动分发出去
                    scrollConsumed[1] = 0
                    dispatchNestedPreScroll(0, deltaY, scrollConsumed, null, ViewCompat.TYPE_TOUCH)
                    deltaY -= scrollConsumed[1]

                    //自己处理滑动
                    val unConsumedY = doScroll(deltaY)
                    val consumedY = deltaY - unConsumedY

                    //将自己滑动的距离分发出去
                    scrollConsumed[1] = 0
                    dispatchNestedScroll(0, consumedY, 0, unConsumedY, null, ViewCompat.TYPE_TOUCH, scrollConsumed)
                }
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                val yVelocity = velocityTracker?.yVelocity ?: 0f
                if (abs(yVelocity) >= minimumFlingVelocity) {
                    //先尝试把 fling 分发出去给自己的支持嵌套滑动的 parent 处理
                    if (!dispatchNestedPreFling(0f, -yVelocity)) {
                        //没有支持嵌套滑动的 parent 消费 fling, 那就我们自己消费
                        dispatchNestedFling(0f, -yVelocity, true)
                        //执行 fling 操作
                        doFling(-yVelocity.toInt())
                    }
                }
                endDrag()
            }
            MotionEvent.ACTION_CANCEL -> {
                endDrag()
            }
        }

        //追踪速度
        velocityTracker?.addMovement(event)

        return true
    }

    private fun endDrag() {
        isBeingDragged = false
        //尝试回收速度追踪器
        recycleVelocityTracker()
        //停止嵌套滑动 - TYPE_TOUCH
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            recycleVelocityTracker()
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun computeVerticalScrollRange(): Int {
        return commentTitleLayout.height + commentRcv.height
    }

    override fun computeVerticalScrollExtent(): Int {
        return 0
    }

    private fun doScroll(dy: Int): Int {
        var unConsumedY = dy
        if (dy > 0) {   //上滑
            if (webView.canScrollVertically(1)) {
                //优先滑动 webView
                unConsumedY = scrollWebView(dy)
                if (unConsumedY != 0 && canScrollVertically(1)) {
                    //webView 还有未消费完的再给自己滑动
                    unConsumedY = scrollSelf(unConsumedY)
                }
            } else if (canScrollVertically(1)) {
                //尝试滑动自己
                unConsumedY = scrollSelf(dy)
            } else if (commentRcv.canScrollVertically(1)) {
                //最后尝试滑动列表
                commentRcv.scrollBy(0, dy)
                unConsumedY = 0
            }
            //如果还有未消费完的距离, 就都交给列表滑动
            if (unConsumedY != 0 && commentRcv.canScrollVertically(1)) {
                commentRcv.scrollBy(0, unConsumedY)
                unConsumedY = 0
            }
        } else if (dy < 0) {    //下滑
            if (commentRcv.canScrollVertically(-1)) {
                //优先滑动列表
                commentRcv.scrollBy(0, dy)
                unConsumedY = 0
            } else if (canScrollVertically(-1)) {
                //再滑动自己
                unConsumedY = scrollSelf(dy)
                if (unConsumedY != 0 && webView.canScrollVertically(-1)) {
                    //自己还有未消费完的再给 webView 滑动
                    unConsumedY = scrollWebView(unConsumedY)
                }
            } else if (webView.canScrollVertically(-1)) {
                //最后滑动 webView
                unConsumedY = scrollWebView(dy)
            }
        }
        Log.d("tag", "doScroll dy: $dy, unConsumedY: $unConsumedY")
        return unConsumedY
    }

    /**
     * 滑动 webView
     *
     * @return 返回未消费的距离
     */
    private fun scrollWebView(dy: Int): Int {
        //可滚动的范围
        val scrollRange = webView.getVerticalScrollRange()
        //当前已滚动的距离
        val scrollY = webView.getVerticalScrollOffset()
        var consumedY = dy
        val newScrollY = scrollY + dy
        if (dy >= 0) {
            //上滑
            if (newScrollY > scrollRange) {
                consumedY = scrollRange - scrollY
            }
        } else {
            //下滑
            if (newScrollY < 0) {
                consumedY = -scrollY
            }
        }
        webView.scrollBy(0, consumedY)
        return dy - consumedY
    }

    /**
     * 滑动自己
     *
     * @return 返回未消费的距离
     */
    private fun scrollSelf(dy: Int): Int {
        //可滚动的范围
        val scrollRange = computeVerticalScrollRange()
        //当前已滚动的距离
        val scrollY = computeVerticalScrollOffset()
        var consumedY = dy
        val newScrollY = scrollY + dy
        if (newScrollY < 0) {
            consumedY = -scrollY
        } else if (newScrollY > scrollRange) {
            consumedY = scrollRange - scrollY
        }
        scrollBy(0, consumedY)
        return dy - consumedY
    }

    private fun doFling(yVelocity: Int) {
        lastScrollerY = scrollY
        scroller.fling(
            0, lastScrollerY,
            0, yVelocity,
            0, 0,
            Int.MIN_VALUE, Int.MAX_VALUE,
        )
        //开始嵌套滑动 - TYPE_NON_TOUCH
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)
        //开始 fling 滑动
        ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun computeScroll() {
        if (scroller.isFinished) {
            return
        }

        scroller.computeScrollOffset()
        val currentScrollY = scroller.currY
        var deltaY = currentScrollY - lastScrollerY
        lastScrollerY = currentScrollY

        //先将滑动分发出去
        scrollConsumed[1] = 0
        dispatchNestedPreScroll(0, deltaY, scrollConsumed, null, ViewCompat.TYPE_NON_TOUCH)
        deltaY -= scrollConsumed[1]

        //记录未消费的滑动距离
        var unConsumedY = deltaY
        if (deltaY != 0) {
            //自己滑动
            unConsumedY = doScroll(deltaY)
            val consumedY = deltaY - unConsumedY

            //将自己滑动的距离分发出去
            scrollConsumed[1] = 0
            dispatchNestedScroll(0, consumedY, 0, unConsumedY, null, ViewCompat.TYPE_NON_TOUCH, scrollConsumed)
            unConsumedY -= scrollConsumed[1]
        }

        if (unConsumedY != 0) {
            //如果还有未消费完的 fling 滑动, 说明滑动到边界了, 停止滑动
            scroller.abortAnimation()
        }

        if (!scroller.isFinished) {
            //滑动未停止, 继续滑动
            ViewCompat.postInvalidateOnAnimation(this)
        } else {
            //停止嵌套滑动 - TYPE_NON_TOUCH
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
        }
    }

    /**
     * NestedScrollingParent3
     */
    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type)
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        parentHelper.onStopNestedScroll(target, type)
        stopNestedScroll(type)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        var unConsumedY = dy
        //上滑, 优先让自己滑动, 自己滑不动了再给子 view 滑动
        if (dy > 0 && canScrollVertically(1)) {
            unConsumedY = scrollSelf(dy)
        }
        consumed[1] = dy - unConsumedY
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        onNestedScrollInternal(dyUnconsumed, type, null)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        onNestedScrollInternal(dyUnconsumed, type, consumed)
    }

    private fun onNestedScrollInternal(dyUnconsumed: Int, type: Int, consumed: IntArray?) {
        //下滑, 优先让子 view 滑动, 子 view 滑不动了再给自己滑动
        var unConsumedY = dyUnconsumed
        if (dyUnconsumed < 0 && canScrollVertically(-1)) {
            unConsumedY = scrollSelf(dyUnconsumed)
        }
        if (consumed != null) {
            consumed[1] += dyUnconsumed - unConsumedY
        }
    }

    /**
     * TODO wangpan 2022/02/26 备注
     * 测试发现如果 target 是 RecyclerView, 它对 fling 的处理是有一些问题, 所以这里由我们来处理
     */
    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        //收到子 view 的 fling 之前事件, 先尝试把 fling 分发出去给自己的支持嵌套滑动的 parent 处理
        if (!dispatchNestedPreFling(velocityX, velocityY)) {
            //没有支持嵌套滑动的 parent 消费 fling, 那就我们自己消费
            dispatchNestedFling(0f, velocityY, true)
            //执行 fling 操作
            doFling(velocityY.toInt())
        }
        /**
         * 返回 true 表示这个 fling 被消费了, 因为只有两种情况:
         * 1. 我的支持嵌套滑动的 parent 消费
         * 2. 我自己消费
         */
        return true
    }

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        //收到子 view 的 fling 完后事件
        if (consumed) {
            //fling 已经被子 view 消费了, 那我们就不处理
            return false
        }
        //先尝试把 fling 分发出去给自己的支持嵌套滑动的 parent 处理
        if (!dispatchNestedPreFling(velocityX, velocityY)) {
            //没有支持嵌套滑动的 parent 消费 fling, 那就我们自己消费
            dispatchNestedFling(0f, velocityY, true)
            //执行 fling 操作
            doFling(velocityY.toInt())
        }
        return true
    }

    /**
     * NestedScrollingChild3
     */
    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return childHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return childHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type
        )
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
            consumed
        )
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

}