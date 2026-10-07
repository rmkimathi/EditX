package com.editx.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView

class CustomHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var isWrapEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                scrollTo(0, 0)
            }
            requestLayout()
            invalidate()
        }

    override fun measureChildWithMargins(
        child: View?,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        if (isWrapEnabled && child != null) {
            val lp = child.layoutParams as MarginLayoutParams
            val childWidthMeasureSpec = getChildMeasureSpec(
                parentWidthMeasureSpec,
                paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin + widthUsed,
                lp.width
            )
            val childHeightMeasureSpec = getChildMeasureSpec(
                parentHeightMeasureSpec,
                paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + heightUsed,
                lp.height
            )
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
        } else {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (isWrapEnabled) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (isWrapEnabled) return false
        if (ev?.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return super.onTouchEvent(ev)
    }
}
