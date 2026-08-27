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
        val contact = contacts[position]
        if (useStarForFavorites && contact.starred == 1) {
            "*"
        } else {
            sanitizeFirstLetter(contact.getNameToDisplay())
        }
    } catch (e: IndexOutOfBoundsException) {
        ""
    }

    FastScrollItemIndicator.Text(initialLetter)
})

// Returns the first real letter/digit of a name, skipping emojis and symbols.
private fun sanitizeFirstLetter(name: String): String {
    if (name.isEmpty()) return ""
    var index = 0
    while (index < name.length) {
        val cp = name.codePointAt(index)
        val c = String(Character.toChars(cp))[0]
        if (isEmojiCodePoint(cp) || !c.isLetterOrDigit()) {
            index += Character.charCount(cp)
            continue
        }
        return c.toString().uppercase()
    }
    return ""
}

private fun isEmojiCodePoint(cp: Int): Boolean {
    return cp in 0x1F000..0x1FAFF || // emoticons, symbols, pictographs
        cp in 0x2600..0x27BF || // misc symbols / dingbats
        cp == 0x00A9 || cp == 0x00AE || cp == 0x2122 || cp == 0x3030 || // © ® ™ 〰
        cp in 0xFE00..0xFE0F || cp in 0xFE20..0xFE2F // variation selectors
}
