package org.koitharu.kotatsu.core.ui

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.viewbinding.ViewBinding
import org.koitharu.kotatsu.core.ui.util.SystemUiController

abstract class BaseFullscreenActivity<B : ViewBinding> :
	BaseActivity<B>() {

    protected lateinit var systemUiController: SystemUiController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(window) {
            systemUiController = SystemUiController(this)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        }
        systemUiController.setSystemUiVisible(true)
    }
}
