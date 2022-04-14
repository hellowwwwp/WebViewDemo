package com.example.webviewdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.webviewdemo.databinding.ActivityNestedScrollWebViewBinding

class NestedScrollWebViewActivity : AppCompatActivity() {

    private val viewBinding: ActivityNestedScrollWebViewBinding by lazy {
        ActivityNestedScrollWebViewBinding.inflate(layoutInflater)
    }

    private val refreshLayout: SwipeRefreshLayout
        get() = viewBinding.refreshLayout

    private val webView: WebView
        get() = viewBinding.webView

    private var webUrlIndex: Int = -1

    private val webUrlList: List<String> = listOf(
        "https://github.com/wp292519413/WebViewDemo/blob/master/app/src/main/java/com/example/webviewdemo/NestedWebLinearLayout.kt",
        "https://juejin.cn/book/6984685333312962573",
        "https://juejin.cn/book/6893424020042022925"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initWebView()
    }

    private fun getWebUrl(): String {
        webUrlIndex++
        if (webUrlIndex >= webUrlList.size) {
            webUrlIndex = 0
        }
        return webUrlList[webUrlIndex]
    }

    private fun initWebView() {
        with(refreshLayout) {
            setOnRefreshListener {
                postDelayed({
                    webView.loadUrl(getWebUrl())
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
            loadUrl(getWebUrl())
        }
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