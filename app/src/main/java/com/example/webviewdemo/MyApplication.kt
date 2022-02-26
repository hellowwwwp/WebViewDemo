package com.example.webviewdemo

import android.app.Application

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/2/26
 */
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        application = this
    }

    companion object {

        lateinit var application: Application

    }

}