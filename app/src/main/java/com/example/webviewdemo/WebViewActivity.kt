package com.example.webviewdemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.webviewdemo.databinding.ActivityWebViewBinding
import com.example.webviewdemo.ext.toast

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/2/26
 */
class WebViewActivity : AppCompatActivity() {

    private val viewBinding: ActivityWebViewBinding by lazy {
        ActivityWebViewBinding.inflate(layoutInflater)
    }

    private val refreshLayout: SwipeRefreshLayout
        get() = viewBinding.refreshLayout

    private val webView: WebView
        get() = viewBinding.webView

    private val titleLayout: View
        get() = viewBinding.commentTitleLayout

    private val commentRcv: RecyclerView
        get() = viewBinding.commentRcv

    private var webUrlIndex: Int = -1

    private val webUrlList: List<String> = listOf(
        "https://juejin.cn/book/6984685333312962573",
        "https://juejin.cn/book/6844733722583891976",
        "https://juejin.cn/book/6844733819363262472",
        "https://juejin.cn/book/6844733735686914062",
        "https://juejin.cn/book/6893424020042022925"
    )

    private val commentAdapter: CommentAdapter by lazy {
        CommentAdapter().apply {
            val itemList = mutableListOf<CommentItem>()
            for (i in 0 until 40) {
                itemList.add(CommentItem(i, "这是一条评论来的: $i"))
            }
            submitList(itemList)
        }
    }

    private var isDisallowInterceptTouchEvent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initView()

        with(viewBinding) {
            openDisallowBtn.setOnClickListener {
                isDisallowInterceptTouchEvent = true
                "已开启 parent 禁用事件拦截".toast()
            }
            closeDisallowBtn.setOnClickListener {
                isDisallowInterceptTouchEvent = false
                "已关闭 parent 禁用事件拦截".toast()
            }
            openRefreshBtn.setOnClickListener {
                refreshLayout.isEnabled = true
                "已开启下拉刷新".toast()
            }
            closeRefreshBtn.setOnClickListener {
                refreshLayout.isEnabled = false
                "已关闭下拉刷新".toast()
            }
        }
    }

    private fun refreshWebView() {
        webUrlIndex++
        if (webUrlIndex >= webUrlList.size) {
            webUrlIndex = 0
        }
        val url = webUrlList[webUrlIndex]
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initView() {
        with(refreshLayout) {
            setOnRefreshListener {
                postDelayed({
                    refreshWebView()
                    isRefreshing = false
                }, 1000)
            }
        }

        with(webView) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            val cachePath = cacheDir.absolutePath
            settings.setAppCacheEnabled(true)
            settings.setAppCachePath(cachePath)
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            loadUrl(webUrlList[0])
        }

        with(commentRcv) {
            layoutManager = LinearLayoutManager(this@WebViewActivity)
            adapter = commentAdapter
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (isDisallowInterceptTouchEvent && e.action == MotionEvent.ACTION_DOWN) {
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {

                }

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {

                }
            })
        }

        titleLayout.setOnClickListener {
            "点击 title 干什么?".toast()
        }

        refreshWebView()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}