package org.koitharu.kotatsu.core.ui.util

import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

sealed class SystemUiController(
	protected val window: Window,
) {

    abstract fun setSystemUiVisible(value: Boolean)

    private class Api30Impl(window: Window) : SystemUiController(window) {

        private val insetsController = checkNotNull(window.decorView.windowInsetsController)

        override fun setSystemUiVisible(value: Boolean) {
            if (value) {
                insetsController.show(WindowInsets.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            } else {
                insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsets.Type.systemBars())
            }
        }
    }

    companion object {
        operator fun invoke(window: Window): SystemUiController =
            Api30Impl(window)
    }
}
