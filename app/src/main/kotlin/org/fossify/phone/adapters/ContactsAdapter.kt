package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemContactStarBinding
import org.fossify.phone.databinding.ItemContactStarGridBinding
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.callContactWithSim
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.interfaces.RefreshItemsListener
import java.util.Collections

private const val VIEW_TYPE_SEPARATOR = Int.MIN_VALUE

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    private val refreshItemsListener: RefreshItemsListener? = null,
    var viewType: Int = VIEW_TYPE_LIST,
    private val showDeleteButton: Boolean = true,
    private val enableDrag: Boolean = false,
    private val allowLongClick: Boolean = true,
    private val enableSwipeActions: Boolean = false,
    var favoriteCount: Int = 0,
    itemClick: (Any) -> Unit,
    val profileIconClick: ((Any) -> Unit)? = null
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    ItemTouchHelperContract, MyRecyclerView.MyZoomListener {

    private var textToHighlight = highlightText
    var fontSize: Float = activity.getTextSize()
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null
    var onDragEndListener: (() -> Unit)? = null
    var onSpanCountListener: (Int) -> Unit = {}

    // swipe-to-call / swipe-to-sms gesture state
    private val swipeTouchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val swipeIndicatorThreshold = 80f * resources.displayMetrics.density
    private val swipeActionThreshold = 140f * resources.displayMetrics.density
    private val swipeCallColor = Color.argb(150, 76, 175, 80)
    private val swipeSmsColor = Color.argb(150, 33, 150, 243)
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeWasDragging = false

    // whether a separator row is shown after the favorite block
    private fun hasSeparator() = favoriteCount > 0

    // adapter position -> contact list index; returns -1 for the separator row
    private fun contactIndexAt(position: Int): Int {
        if (!hasSeparator()) {
            return position
        }
        return when {
            position < favoriteCount -> position
            position == favoriteCount -> -1
            else -> position - 1
        }
    }

    // contact list index -> adapter position
    private fun adapterPositionFor(index: Int): Int {
        return if (hasSeparator() && index >= favoriteCount) index + 1 else index
    }

    init {
        setupDragListener(true)

        if (recyclerView.layoutManager is GridLayoutManager) {
            setupZoomListener(this)
        }

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this, viewType == VIEW_TYPE_GRID))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val isOneItemSelected = isOneItemSelected()
        val selectedNumber = getSelectedPhoneNumber().orEmpty()

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_delete).isVisible = showDeleteButton
            findItem(R.id.cab_create_shortcut).title = activity.addLockedLabelIfNeeded(R.string.create_shortcut)
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected
            findItem(R.id.cab_block_unblock_contact).isVisible = isOneItemSelected && isNougatPlus()

            val selectedFavorites = getSelectedItems().count { it.starred == 1 }
            val allFavorites = selectedFavorites > 0 && selectedFavorites == selectedKeys.size
            findItem(R.id.cab_favorite).apply {
                isVisible = isOneItemSelected
                if (allFavorites) {
                    setIcon(R.drawable.ic_star_outline_vector)
                    setTitle(R.string.remove_from_favorites)
                } else {
                    setIcon(R.drawable.ic_star_vector)
                    setTitle(R.string.add_to_favorites)
                }
            }

            getCabBlockContactTitle { title ->
                findItem(R.id.cab_block_unblock_contact).title = title
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_block_unblock_contact -> tryBlockingUnblocking()
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_view_details -> viewContactDetails()
            R.id.cab_favorite -> toggleFavoritesForSelected()
            R.id.cab_create_shortcut -> tryCreateShortcut()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = contactIndexAt(position) != -1

    override fun getItemSelectionKey(position: Int): Int? {
        val index = contactIndexAt(position)
        return if (index == -1) null else contacts.getOrNull(index)?.rawId
    }

    override fun getItemKeyPosition(key: Int): Int {
        val index = contacts.indexOfFirst { it.rawId == key }
        return if (index == -1) -1 else adapterPositionFor(index)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (viewType == VIEW_TYPE_SEPARATOR) {
            val view = layoutInflater.inflate(R.layout.item_contact_separator, parent, false)
            view.setOnClickListener(null)
            view.setOnLongClickListener(null)
            return createViewHolder(view)
        }
        val binding = Binding.getByItemViewType(viewType).inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun getItemViewType(position: Int): Int {
        return if (contactIndexAt(position) == -1) VIEW_TYPE_SEPARATOR else viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contactIndex = contactIndexAt(position)
        if (contactIndex == -1) {
            bindViewHolder(holder)
            return
        }
        val contact = contacts[contactIndex]
        holder.bindView(contact, true, allowLongClick) { itemView, _ ->
            val itemViewType = getItemViewType(position)
            setupView(Binding.getByItemViewType(itemViewType).bind(itemView), contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size + (if (hasSeparator()) 1 else 0)

    private fun getCabBlockContactTitle(callback: (String) -> Unit) {
        val contact = getSelectedItems().firstOrNull() ?: return callback("")

        activity.isContactBlocked(contact) { blocked ->
            val cabItemTitleRes = if (blocked) {
                R.string.unblock_contact
            } else {
                R.string.block_contact
            }

            callback(activity.addLockedLabelIfNeeded(cabItemTitleRes))
        }
    }

    private fun tryBlockingUnblocking() {
        val contact = getSelectedItems().firstOrNull() ?: return

        if (activity.isOrWasThankYouInstalled()) {
            activity.isContactBlocked(contact) { blocked ->
                if (blocked) {
                    tryUnblocking(contact)
                } else {
                    tryBlocking(contact)
                }
            }
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    private fun tryBlocking(contact: Contact) {
        askConfirmBlock(contact) { contactBlocked ->
            val resultMsg = if (contactBlocked) {
                R.string.block_contact_success
            } else {
                R.string.block_contact_fail
            }

            activity.toast(resultMsg)
            finishActMode()
        }
    }

    private fun tryUnblocking(contact: Contact) {
        val contactUnblocked = activity.unblockContact(contact)
        val resultMsg = if (contactUnblocked) {
            R.string.unblock_contact_success
        } else {
            R.string.unblock_contact_fail
        }

        activity.toast(resultMsg)
        finishActMode()
    }

    private fun askConfirmBlock(contact: Contact, callback: (Boolean) -> Unit) {
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), contact.name)

        ConfirmationDialog(activity, question) {
            val contactBlocked = activity.blockContact(contact)
            callback(contactBlocked)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<Contact>, highlightText: String = "", newFavoriteCount: Int = favoriteCount) {
        if (newItems.hashCode() != contacts.hashCode() || newFavoriteCount != favoriteCount) {
            contacts = ArrayList(newItems)
            favoriteCount = newFavoriteCount
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun callContact(useSimOne: Boolean) {
        val number = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(number, useSimOne)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM(phoneNumber)
        finishActMode()
    }

    private fun sendSMS() {
        val numbers = ArrayList<String>()
        getSelectedItems().map { simpleContact ->
            val contactNumbers = simpleContact.phoneNumbers
            val primaryNumber = contactNumbers.firstOrNull { it.isPrimary }
            val normalizedNumber = primaryNumber?.normalizedNumber ?: contactNumbers.firstOrNull()?.normalizedNumber

            if (normalizedNumber != null) {
                numbers.add(normalizedNumber)
            }
        }

        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.startContactDetailsIntent(contact)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.getNameToDisplay()}\""
        } else {
            resources.getQuantityString(R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contacts.removeAll(contactsToRemove)
        if (favoriteCount > 0) {
            favoriteCount = contacts.takeWhile { it.starred == 1 }.size
        }
        val idsToRemove = contactsToRemove.map { it.rawId }.toMutableList() as ArrayList<Int>

        SimpleContactsHelper(activity).deleteContactRawIDs(idsToRemove) {
            activity.runOnUiThread {
                if (contacts.isEmpty()) {
                    refreshItemsListener?.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun getSelectedItems() = contacts.filter { selectedKeys.contains(it.rawId) } as ArrayList<Contact>

    private fun toggleFavoritesForSelected() {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }

        val addAll = selectedItems.any { it.starred != 1 }

        activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
            val helper = ContactsHelper(activity)
            if (addAll) {
                helper.addFavorites(selectedItems)
            } else {
                helper.removeFavorites(selectedItems)
            }

            activity.runOnUiThread {
                finishActMode()
                refreshItemsListener?.refreshItems()
            }
        }
    }

    private fun getSelectedPhoneNumber(): String? {
        return getSelectedItems().firstOrNull()?.getPrimaryNumber()
    }

    private fun tryCreateShortcut() {
        if (activity.isOrWasThankYouInstalled()) {
            createShortcut()
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.getNameToDisplay()) { image ->
                activity.runOnUiThread {
                    activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val intent = Intent(action).apply {
                            data = Uri.fromParts("tel", getSelectedPhoneNumber(), null)
                        }

                        val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                            .setShortLabel(contact.getNameToDisplay())
                            .setIcon(Icon.createWithBitmap(image))
                            .setIntent(intent)
                            .build()

                        manager.requestPinShortcut(shortcut, null)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (holder.itemViewType == VIEW_TYPE_SEPARATOR) {
            return
        }
        if (!activity.isDestroyed && !activity.isFinishing) {
            Binding.getByItemViewType(holder.itemViewType).bind(holder.itemView).apply {
                Glide.with(activity).clear(itemContactImage)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(binding: ItemViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            root.setupViewBackground(activity)
            root.translationX = 0f
            itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)

            itemContactImage.apply {
                if (profileIconClick != null && viewType != VIEW_TYPE_GRID) {
                    setBackgroundResource(R.drawable.selector_clickable_circle)

                    setOnClickListener {
                        if (!actModeCallback.isSelectable) {
                            profileIconClick.invoke(contact)
                        } else {
                            holder.viewClicked(contact)
                        }
                    }
                    setOnLongClickListener {
                        holder.viewLongClicked()
                        true
                    }
                }
            }

            itemContactStar.beVisibleIf(contact.starred == 1)

            itemContactName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                val name = contact.getNameToDisplay()
                text = if (textToHighlight.isEmpty()) {
                    name
                } else {
                    val normalizedName = name.normalizeString()
                    val normalizedSearchText = textToHighlight.normalizeString()
                    if (normalizedName.contains(normalizedSearchText, true)) {
                        name.highlightTextPart(normalizedSearchText, properPrimaryColor)
                    } else {
                        var spacedTextToHighlight = textToHighlight
                        val strippedName = name.filterNot { it.isWhitespace() }
                        val strippedDigits = KeypadHelper.convertKeypadLettersToDigits(strippedName)
                        val startIndex = strippedDigits.indexOf(textToHighlight)

                        if (strippedDigits.contains(textToHighlight)) {
                            for (i in spacedTextToHighlight.indices) {
                                if (startIndex + i < name.length && name[startIndex + i].isWhitespace()) {
                                    spacedTextToHighlight = spacedTextToHighlight.replaceRange(i, i, " ")
                                }
                            }
                        }

                        name.highlightTextFromNumbers(spacedTextToHighlight, properPrimaryColor)
                    }
                }
            }

            if (enableDrag && textToHighlight.isEmpty()) {
                dragHandleIcon.apply {
                    val isFavorite = favoriteCount == 0 || contacts.indexOf(contact) < favoriteCount
                    beVisibleIf(selectedKeys.isNotEmpty() && isFavorite)
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragHandleIcon.apply {
                    if (viewType == VIEW_TYPE_GRID) {
                        beVisible()
                    } else {
                        beGone()
                    }
                    setOnTouchListener(null)
                }
            }

            setupSwipeActions(binding, contact)

            if (!activity.isDestroyed) {
                SimpleContactsHelper(root.context).loadContactImage(contact.photoUri, itemContactImage, contact.getNameToDisplay())
            }
        }
    }

    // lets the user swipe a contact row sideways to quickly call (right, green) or SMS (left, blue) it
    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeActions(binding: ItemViewBinding, contact: Contact) {
        val view = binding.root
        if (!enableSwipeActions || viewType == VIEW_TYPE_GRID) {
            view.setOnTouchListener(null)
            return
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    swipeWasDragging = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (actModeCallback.isSelectable) {
                        return@setOnTouchListener false
                    }
                    val dx = event.rawX - swipeStartX
                    val dy = event.rawY - swipeStartY
                    if (!swipeWasDragging) {
                        if (Math.abs(dx) > swipeTouchSlop && Math.abs(dx) > Math.abs(dy)) {
                            swipeWasDragging = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            // unpress the row so any pending long-press stops firing while we drag
                            v.isPressed = false
                        } else {
                            return@setOnTouchListener false
                        }
                    }

                    v.translationX = dx
                    v.setBackgroundColor(
                        when {
                            dx > swipeIndicatorThreshold -> swipeCallColor
                            dx < -swipeIndicatorThreshold -> swipeSmsColor
                            else -> Color.TRANSPARENT
                        }
                    )
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (swipeWasDragging) {
                        val dx = event.rawX - swipeStartX
                        if (dx > swipeActionThreshold) {
                            performSwipeCall(contact)
                        } else if (dx < -swipeActionThreshold) {
                            performSwipeSms(contact)
                        }
                        resetSwipeState(v)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (swipeWasDragging) {
                        resetSwipeState(v)
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    private fun resetSwipeState(view: View) {
        view.animate().translationX(0f).setDuration(150).withEndAction {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.setupViewBackground(activity)
        }.start()
        swipeWasDragging = false
    }

    private fun performSwipeCall(contact: Contact) {
        (activity as SimpleActivity).startCallWithConfirmationCheck(contact)
    }

    private fun performSwipeSms(contact: Contact) {
        val number = contact.getPrimaryNumber()
        if (!number.isNullOrEmpty()) {
            activity.launchSendSMSIntent(number)
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val fromIndex = contactIndexAt(fromPosition)
        val toIndex = contactIndexAt(toPosition)
        if (fromIndex == -1 || toIndex == -1) {
            return
        }

        // only favorites (the leading block) can be dragged
        if (favoriteCount > 0 && (fromIndex >= favoriteCount || toIndex >= favoriteCount)) {
            return
        }

        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contacts, contactIndexAt(i), contactIndexAt(i + 1))
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contacts, contactIndexAt(i), contactIndexAt(i - 1))
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }

    override fun zoomIn() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount - 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    override fun zoomOut() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount + 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    private sealed interface Binding {
        companion object {
            fun getByItemViewType(viewType: Int): Binding {
                return when (viewType) {
                    VIEW_TYPE_GRID -> ItemContactGrid
                    else -> ItemContact
                }
            }
        }

        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding

        fun bind(view: View): ItemViewBinding

        data object ItemContactGrid : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactStarGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactStarGridBinding.bind(view))
            }
        }

        data object ItemContact : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactStarBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactStarBinding.bind(view))
            }
        }
    }

    private interface ItemViewBinding : ViewBinding {
        val itemContactName: TextView
        val itemContactImage: ImageView
        val itemContactFrame: ConstraintLayout
        val dragHandleIcon: ImageView
        val itemContactStar: ImageView
    }

    private class ItemContactGridBindingAdapter(val binding: ItemContactStarGridBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactStar = binding.itemContactStar

        override fun getRoot(): View = binding.root
    }

    private class ItemContactBindingAdapter(val binding: ItemContactStarBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactStar = binding.itemContactStar

        override fun getRoot(): View = binding.root
    }
}
