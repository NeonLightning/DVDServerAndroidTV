package com.neonlightning.dvdserver

import android.util.Log

object AppLogger {
    private const val TAG = "DVDServer"

    fun d(message: String, throwable: Throwable? = null) {
        Log.d(TAG, message, throwable)
    }

    fun i(message: String, throwable: Throwable? = null) {
        Log.i(TAG, message, throwable)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
