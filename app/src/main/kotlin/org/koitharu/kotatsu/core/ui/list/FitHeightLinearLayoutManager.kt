package org.koitharu.kotatsu.core.ui.list

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutParams

class FitHeightLinearLayoutManager : LinearLayoutManager {

	constructor(context: Context) : super(context)

	override fun layoutDecoratedWithMargins(child: View, left: Int, top: Int, right: Int, bottom: Int) {
		if (orientation == RecyclerView.VERTICAL && child.layoutParams.height == LayoutParams.MATCH_PARENT) {
			val parentBottom = height - paddingBottom
			val offset = parentBottom - bottom
			super.layoutDecoratedWithMargins(child, left, top, right, bottom + offset)
		} else {
			super.layoutDecoratedWithMargins(child, left, top, right, bottom)
		}
	}
}
