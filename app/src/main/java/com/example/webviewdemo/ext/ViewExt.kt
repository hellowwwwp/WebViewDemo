package com.example.webviewdemo.ext

import android.view.MotionEvent
import android.widget.Toast
import com.example.webviewdemo.MyApplication

val MotionEvent.actionName: String
    get() {
        return when (this.action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "unknown"
        }
    }

fun String.toast(duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(MyApplication.application, this, duration).show()
}