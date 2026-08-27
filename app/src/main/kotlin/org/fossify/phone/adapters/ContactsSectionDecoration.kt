package org.fossify.phone.adapters

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.models.contacts.Contact

class ContactsSectionDecoration(
    private val contactsProvider: () -> List<Contact>,
    private val dividerColor: Int = DEFAULT_COLOR,
    private val dividerHeight: Int = 3
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        color = dividerColor
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        val contacts = contactsProvider()
        val firstRegular = contacts.indexOfFirst { it.starred != 1 }
        if (firstRegular <= 0) return

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == NO_POSITION) continue
            // The separator sits at the top of the first non-favorite row.
            if (position != firstRegular) continue

            val dividerY = child.top.toFloat() - child.translationY
            c.drawRect(
                parent.paddingLeft.toFloat(),
                dividerY - dividerHeight,
                parent.width.toFloat() - parent.paddingRight,
                dividerY,
                paint
            )
        }
    }

    companion object {
        private const val NO_POSITION = -1
        private const val DEFAULT_COLOR = 0x33000000
    }
}
