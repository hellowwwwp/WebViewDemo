package com.example.webviewdemo

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/2/26
 */
class MyWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    fun getVerticalScrollOffset(): Int {
        return computeVerticalScrollOffset()
    }

    fun getVerticalScrollRange(): Int {
        return computeVerticalScrollRange() - computeVerticalScrollExtent()
    }
}