package org.koitharu.kotatsu.main.ui

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import org.koitharu.kotatsu.core.ui.util.ShrinkOnScrollBehavior
import org.koitharu.kotatsu.core.ui.widgets.SlidingBottomNavigationView

class MainActionButtonBehavior : ShrinkOnScrollBehavior {

	constructor() : super()

	override fun layoutDependsOn(
		parent: CoordinatorLayout,
		child: ExtendedFloatingActionButton,
		dependency: View
	): Boolean {
		return dependency is SlidingBottomNavigationView || super.layoutDependsOn(parent, child, dependency)
	}

	override fun onDependentViewChanged(
		parent: CoordinatorLayout,
		child: ExtendedFloatingActionButton,
		dependency: View
	): Boolean {
		val bottom = child.bottom
		val bottomLine = parent.height
		return if (bottom > bottomLine) {
			ViewCompat.offsetTopAndBottom(child, bottomLine - bottom)
			true
		} else {
			false
		}
	}
}
