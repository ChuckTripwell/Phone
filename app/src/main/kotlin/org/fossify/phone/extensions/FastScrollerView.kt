package org.fossify.phone.extensions

import androidx.recyclerview.widget.RecyclerView
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView
import org.fossify.commons.models.contacts.Contact

fun FastScrollerView.setupWithContacts(
    recyclerView: RecyclerView,
    contacts: List<Contact>,
    useStarForFavorites: Boolean = false,
) = setupWithRecyclerView(recyclerView, { position ->
    val initialLetter = try {
        if (useStarForFavorites && contacts[position].starred == 1) {
            "*"
        } else {
            contacts[position].getFirstLetter()
        }
    } catch (e: IndexOutOfBoundsException) {
        ""
    }

    FastScrollItemIndicator.Text(initialLetter)
})
