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
        var foundFavorite = false
        var foundFavoritesEnd = false

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val contact = contacts.getOrNull(position) ?: continue
            if (contact.starred == 1) {
                foundFavorite = true
            } else if (!foundFavoritesEnd && foundFavorite) {
                foundFavoritesEnd = true
                val dividerY = child.top.toFloat() - child.translationY
                c.drawRect(
                    parent.paddingLeft.toFloat() + 0f,
                    dividerY - dividerHeight,
                    parent.width.toFloat() - parent.paddingRight,
                    dividerY,
                    paint
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_COLOR = 0x33000000
    }
}
