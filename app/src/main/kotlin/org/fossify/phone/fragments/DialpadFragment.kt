package org.fossify.phone.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.KeypadHelper
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.FragmentDialpadBinding
import org.fossify.phone.extensions.addCharacter
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.boundingBox
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getKeyEvent
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.commons.extensions.value
import org.fossify.phone.helpers.DIALPAD_TONE_LENGTH_MS
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.ToneGeneratorHelper
import org.fossify.phone.models.SpeedDial
import java.util.Locale
import kotlin.math.roundToInt

class DialpadFragment(
    context: Context, attributeSet: AttributeSet,
) : MyViewPagerFragment<MyViewPagerFragment.InnerBinding>(context, attributeSet) {

    private lateinit var binding: FragmentDialpadBinding
    private val mainScope = MainScope()

    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private var initialized = false

    private var hasRussianLocale = false
    private val russianCharsMap by lazy {
        hashMapOf(
            'а' to 2, 'б' to 2, 'в' to 2, 'г' to 2,
            'д' to 3, 'е' to 3, 'ё' to 3, 'ж' to 3, 'з' to 3,
            'и' to 4, 'й' to 4, 'к' to 4, 'л' to 4,
            'м' to 5, 'н' to 5, 'о' to 5, 'п' to 5,
            'р' to 6, 'с' to 6, 'т' to 6, 'у' to 6,
            'ф' to 7, 'х' to 7, 'ц' to 7, 'ч' to 7,
            'ш' to 8, 'щ' to 8, 'ъ' to 8, 'ы' to 8,
            'ь' to 9, 'э' to 9, 'ю' to 9, 'я' to 9
        )
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentDialpadBinding.bind(this)
        innerBinding = DialpadInnerBinding(binding)
    }

    override fun setupFragment() {
        if (initialized) {
            return
        }
        initialized = true

        val act = activity as? SimpleActivity ?: return
        hasRussianLocale = Locale.getDefault().language == "ru"

        binding.apply {
            if (act.config.hideDialpadNumbers) {
                dialpadWrapper.dialpad1Holder.isVisible = false
                dialpadWrapper.dialpad2Holder.isVisible = false
                dialpadWrapper.dialpad3Holder.isVisible = false
                dialpadWrapper.dialpad4Holder.isVisible = false
                dialpadWrapper.dialpad5Holder.isVisible = false
                dialpadWrapper.dialpad6Holder.isVisible = false
                dialpadWrapper.dialpad7Holder.isVisible = false
                dialpadWrapper.dialpad8Holder.isVisible = false
                dialpadWrapper.dialpad9Holder.isVisible = false
                dialpadWrapper.dialpadPlusHolder.isVisible = true
                dialpadWrapper.dialpad0Holder.visibility = View.INVISIBLE
            }

            arrayOf(
                dialpadWrapper.dialpad0Holder,
                dialpadWrapper.dialpad1Holder,
                dialpadWrapper.dialpad2Holder,
                dialpadWrapper.dialpad3Holder,
                dialpadWrapper.dialpad4Holder,
                dialpadWrapper.dialpad5Holder,
                dialpadWrapper.dialpad6Holder,
                dialpadWrapper.dialpad7Holder,
                dialpadWrapper.dialpad8Holder,
                dialpadWrapper.dialpad9Holder,
                dialpadWrapper.dialpadPlusHolder,
                dialpadWrapper.dialpadAsteriskHolder,
                dialpadWrapper.dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, context.theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }
        }

        speedDialValues = act.config.getSpeedDialValues()
        privateCursor = act.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        toneGeneratorHelper = ToneGeneratorHelper(act, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadWrapper.apply {
            if (hasRussianLocale) {
                dialpad2Letters.append("\nАБВГ")
                dialpad3Letters.append("\nДЕЁЖЗ")
                dialpad4Letters.append("\nИЙКЛ")
                dialpad5Letters.append("\nМНОП")
                dialpad6Letters.append("\nРСТУ")
                dialpad7Letters.append("\nФХЦЧ")
                dialpad8Letters.append("\nШЩЪЫ")
                dialpad9Letters.append("\nЬЭЮЯ")

                val fontSize = resources.getDimension(R.dimen.small_text_size)
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }
            }

            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }

        binding.apply {
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput(); true }
            dialpadCallButton.setOnClickListener { initCall(dialpadInput.value) }
            dialpadCallButton.setOnLongClickListener { initCallWithSimSelector() }
            dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
        }

        ContactsHelper(act).getContacts(showOnlyContactsWithNumbers = true) { allContacts ->
            this@DialpadFragment.allContacts = allContacts
            post { dialpadValueChanged(binding.dialpadInput.value) }
        }

        val properPrimaryColor = act.getProperPrimaryColor()
        binding.apply {
            val callIcon = resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_phone_vector,
                color = properPrimaryColor.getContrastColor()
            )
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(properPrimaryColor)
            letterFastscroller.textColor = act.getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        val act = activity as? SimpleActivity ?: return
        binding.letterFastscroller.textColor = act.getProperTextColor().getColorStateList()
        binding.letterFastscroller.pressedTextColor = properPrimaryColor
    }

    override fun onSearchClosed() {
    }

    override fun onSearchQueryChanged(text: String) {
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        binding.dialpadInput.setText("")
    }

    private fun clearInputWithDelay() {
        mainScope.launch {
            delay(1000)
            clearInput()
        }
    }

    private fun dialpadValueChanged(text: String) {
        val act = activity as? SimpleActivity ?: return
        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter { contact ->
            var convertedName = KeypadHelper.convertKeypadLettersToDigits(
                contact.name.normalizeString()
            ).filterNot { it.isWhitespace() }

            if (hasRussianLocale) {
                var currConvertedName = ""
                convertedName.lowercase(Locale.getDefault()).forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedName += convertedChar
                }
                convertedName = currConvertedName
            }

            contact.doesContainPhoneNumber(text) || (convertedName.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text)
        }).toMutableList() as ArrayList<Contact>

        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

        ContactsAdapter(
            activity = act,
            contacts = filtered,
            recyclerView = binding.dialpadList,
            highlightText = text,
            itemClick = {
                act.startCallWithConfirmationCheck(it as Contact)
                clearInputWithDelay()
            },
            profileIconClick = {
                act.startContactDetailsIntent(it as Contact)
            }).apply {
            binding.dialpadList.adapter = this
        }

        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
    }

    private fun initCall(number: String = binding.dialpadInput.value, name: String? = null) {
        val act = activity as? SimpleActivity ?: return
        if (number.isNotEmpty()) {
            act.startCallWithConfirmationCheck(number, name ?: number)
            clearInputWithDelay()
        } else {
            RecentsHelper(act).getRecentCalls(queryLimit = 1) {
                val mostRecentNumber = it.firstOrNull()?.phoneNumber
                if (!mostRecentNumber.isNullOrEmpty()) {
                    post { binding.dialpadInput.setText(mostRecentNumber) }
                }
            }
        }
    }

    private fun initCallWithSimSelector(): Boolean {
        val act = activity as? SimpleActivity ?: return false
        val number = binding.dialpadInput.value
        return if (act.areMultipleSIMsAvailable() && number.isNotEmpty()) {
            act.startCallWithConfirmationCheck(
                recipient = number,
                name = number,
                forceSimSelector = true
            )
            true
        } else {
            false
        }
    }

    private fun speedDial(id: Int): Boolean {
        val act = activity as? SimpleActivity ?: return false
        if (binding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, speedDial.getName(act))
                return true
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        val act = activity as? SimpleActivity ?: return
        if (act.config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        val act = activity as? SimpleActivity ?: return
        if (act.config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        val act = activity as? SimpleActivity ?: return
        if (act.config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }

    private class DialpadInnerBinding(val binding: FragmentDialpadBinding) : InnerBinding {
        override val fragmentList: MyRecyclerView = binding.dialpadList
        override val recentsList = null
    }
}
