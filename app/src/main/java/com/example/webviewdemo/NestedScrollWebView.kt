package com.example.webviewdemo

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.OverScroller
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/4/14
 *
 * 支持纵向的嵌套滑动以及水平方向的正常滑动
 */
class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyle), NestedScrollingChild3, Runnable {

    private val scroller: OverScroller = OverScroller(context)
    private val touchSlop: Int
    private val minimumFlingVelocity: Float
    private val maximumFlingVelocity: Float
    private var velocityTracker: VelocityTracker? = null

    private var lastMotionX: Int = 0
    private var lastMotionY: Int = 0
    private val consumed: IntArray = IntArray(2)

    private var downMotionX: Int = 0
    private var downMotionY: Int = 0

    private var lastScrollY: Int = 0

    private var dragOrientation: DragOrientation? = null

    private var isUpEventDispatched: Boolean = false
    private var isCancelEventDispatched: Boolean = false

    private val childHelper = NestedScrollingChildHelper(this)
    private val scrollConsumed: IntArray = IntArray(2)

    enum class DragOrientation {
        HORIZONTAL, VERTICAL
    }

    init {
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        minimumFlingVelocity = configuration.scaledMinimumFlingVelocity.toFloat()
        maximumFlingVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
        //开启嵌套滑动支持
        isNestedScrollingEnabled = true
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                //如果按下的时候上一次的滚动状态还未结束, 那就结束它
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                lastMotionX = x
                lastMotionY = y

                downMotionX = x
                downMotionY = y

                initOrResetVelocityTracker()

                //开始嵌套滑动 - TYPE_TOUCH
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)

                isUpEventDispatched = false
                isCancelEventDispatched = false
                //处理 webView 的点击和长按: 将 down 事件原封不动传给 super
                super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = lastMotionX - x
                var deltaY = lastMotionY - y

                when {
                    dragOrientation == null && abs(deltaY) >= touchSlop && abs(deltaY) >= abs(deltaX) -> {
                        //纵向滑动
                        dragOrientation = DragOrientation.VERTICAL
                        parent?.requestDisallowInterceptTouchEvent(true)
                        if (deltaY > 0) {
                            deltaY -= touchSlop
                        } else {
                            deltaY += touchSlop
                        }
                    }
                    dragOrientation == null && abs(deltaX) >= touchSlop && abs(deltaX) >= abs(deltaY) -> {
                        //横向滑动
                        dragOrientation = DragOrientation.HORIZONTAL
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                when (dragOrientation) {
                    DragOrientation.VERTICAL -> {
                        lastMotionY = y

                        //先把滑动分发出去
                        scrollConsumed[1] = 0
                        dispatchNestedPreScroll(deltaX, deltaY, scrollConsumed, null, ViewCompat.TYPE_TOUCH)
                        deltaY -= scrollConsumed[1]

                        //自己处理滑动
                        consumed[1] = 0
                        doScroll(deltaY, consumed)
                        val unconsumedY = deltaY - consumed[1]

                        //将自己滑动的距离分发出去
                        scrollConsumed[1] = 0
                        dispatchNestedScroll(
                            0, consumed[1],
                            0, unconsumedY,
                            null,
                            ViewCompat.TYPE_TOUCH,
                            scrollConsumed
                        )

                        //处理 webView 的点击和长按: 已经产生了滑动, 则分发一个 cancel 事件给 super, 阻止 webView 的点击和长按
                        dispatchCancelEventToSuperIfNeed(event)
                    }
                    DragOrientation.HORIZONTAL -> {
                        //处理 webView 的横向滑动: 如果当前是横向滑动则把横向的 move 事件交给 super 处理
                        dispatchMoveEventToSuper(event)
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP -> {
                when (dragOrientation) {
                    DragOrientation.VERTICAL -> {
                        velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity)
                        val yVelocity = velocityTracker?.yVelocity ?: 0f
                        if (abs(yVelocity) >= minimumFlingVelocity) {
                            //先尝试把 fling 分发出去给自己的支持嵌套滑动的 parent 处理
                            if (!dispatchNestedPreFling(0f, yVelocity)) {
                                //没有支持嵌套滑动的 parent 消费 fling, 那就我们自己消费
                                dispatchNestedFling(0f, yVelocity, true)
                                doFling(-yVelocity.toInt())
                            }
                            //处理 webView 的点击和长按: 已经产生了滑动, 则分发一个 cancel 事件给 super, 阻止 webView 的点击和长按
                            dispatchCancelEventToSuperIfNeed(event)
                        } else {
                            //处理 webView 的点击和长按: 如果没有产生滑动, 则分发一个 up 事件给 super, 处理 webView 的点击和长按
                            dispatchUpEventToSuperIfNeed(event)
                        }
                    }
                    DragOrientation.HORIZONTAL -> {
                        //处理 webView 的横向滑动: 分发一个 up 事件给 super, 处理 webView 的横向滑动
                        dispatchUpEventToSuperIfNeed(event)
                    }
                    null -> {
                        //处理 webView 的点击和长按: 如果没有产生滑动, 则分发一个 up 事件给 super, 处理 webView 的点击和长按
                        dispatchUpEventToSuperIfNeed(event)
                    }
                }
                endDrag()
            }
            MotionEvent.ACTION_CANCEL -> {
                dispatchCancelEventToSuperIfNeed(event)
                endDrag()
            }
        }

        initVelocityTrackerIfNotExists()
        velocityTracker?.addMovement(event)

        return true
    }

    private fun dispatchMoveEventToSuper(event: MotionEvent) {
        val superEvent = MotionEvent.obtain(event)
        val offsetY = (downMotionY - event.y)
        //把纵向滑动的抵消掉
        superEvent.offsetLocation(0f, offsetY)
        super.onTouchEvent(superEvent)
        superEvent.recycle()
    }

    private fun dispatchUpEventToSuperIfNeed(event: MotionEvent) {
        //确保 up 和 cancel 事件在一轮触摸事件中只能分发一次
        if (!isUpEventDispatched && !isCancelEventDispatched) {
            isUpEventDispatched = true
            val superEvent = MotionEvent.obtain(event)
            val offsetX = (downMotionX - event.x)
            val offsetY = (downMotionY - event.y)
            if (dragOrientation == DragOrientation.HORIZONTAL) {
                //把纵向滑动的抵消掉
                superEvent.offsetLocation(0f, offsetY)
            } else {
                //把已经产生的滑动抵消掉
                superEvent.offsetLocation(offsetX, offsetY)
            }
            super.onTouchEvent(superEvent)
            superEvent.recycle()
        }
    }

    private fun dispatchCancelEventToSuperIfNeed(event: MotionEvent) {
        if (!isCancelEventDispatched) {
            isCancelEventDispatched = true
            val superEvent = MotionEvent.obtain(event)
            val offsetX = (downMotionX - event.x)
            val offsetY = (downMotionY - event.y)
            //把已经产生的滑动抵消掉
            superEvent.offsetLocation(offsetX, offsetY)
            superEvent.action = MotionEvent.ACTION_CANCEL
            super.onTouchEvent(superEvent)
            superEvent.recycle()
        }
    }

    private fun endDrag() {
        dragOrientation = null
        recycleVelocityTracker()
        //停止嵌套滑动 - TYPE_TOUCH
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    private fun initVelocityTrackerIfNotExists() {
        velocityTracker ?: kotlin.run {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    private fun initOrResetVelocityTracker() {
        velocityTracker?.clear() ?: kotlin.run {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.apply {
            clear()
            velocityTracker = null
        }
    }

    private fun getVerticalScrollOffset(): Int {
        return computeVerticalScrollOffset()
    }

    private fun getVerticalScrollRange(): Int {
        return computeVerticalScrollRange() - computeVerticalScrollExtent()
    }

    private fun doScroll(deltaY: Int, consumed: IntArray) {
        val scrollRange = getVerticalScrollRange()
        val scrollOffset = getVerticalScrollOffset()
        var consumedY = deltaY
        val newScrollOffset = scrollOffset + deltaY
        if (deltaY >= 0) {
            //上滑
            if (newScrollOffset > scrollRange) {
                consumedY = scrollRange - scrollOffset
            }
        } else {
            //下滑
            if (newScrollOffset < 0) {
                consumedY = -scrollOffset
            }
        }
        scrollBy(0, consumedY)
        consumed[1] = consumedY
    }

    private fun doFling(yVelocity: Int) {
        lastScrollY = scrollY
        scroller.fling(
            0, lastScrollY,
            0, yVelocity,
            0, 0,
            Int.MIN_VALUE, Int.MAX_VALUE
        )
        //开始嵌套滑动 - TYPE_NON_TOUCH
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)
        ViewCompat.postOnAnimation(this, this)
    }

    override fun run() {
        if (scroller.computeScrollOffset()) {
            val currentY = scroller.currY
            var deltaY = currentY - lastScrollY
            lastScrollY = currentY

            //先将滑动分发出去
            scrollConsumed[1] = 0
            dispatchNestedPreScroll(0, deltaY, scrollConsumed, null, ViewCompat.TYPE_NON_TOUCH)
            deltaY -= scrollConsumed[1]

            //自己滑动
            consumed[1] = 0
            doScroll(deltaY, consumed)
            var unconsumedY = deltaY - consumed[1]

            //将自己滑动的距离分发出去
            scrollConsumed[1] = 0
            dispatchNestedScroll(0, consumed[1], 0, unconsumedY, null, ViewCompat.TYPE_NON_TOUCH, scrollConsumed)
            unconsumedY -= scrollConsumed[1]

            if (unconsumedY != 0) {
                //如果还有未消费完的滑动距离, 说明滑动到边界了, 停止滑动
                scroller.abortAnimation()
                //停止嵌套滑动 - TYPE_NON_TOUCH
                stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
            } else {
                //继续滑动
                ViewCompat.postOnAnimation(this, this)
            }
        }
    }

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