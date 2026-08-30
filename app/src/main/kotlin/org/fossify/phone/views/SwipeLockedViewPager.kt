package org.fossify.phone.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import org.fossify.commons.views.MyViewPager

// disables swiping left/right to switch between bottom tabs, while keeping tab taps working
class SwipeLockedViewPager(context: Context, attributeSet: AttributeSet) : MyViewPager(context, attributeSet) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun onTouchEvent(ev: MotionEvent): Boolean = false
}
