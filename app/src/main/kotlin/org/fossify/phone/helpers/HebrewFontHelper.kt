package org.fossify.phone.helpers

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.fossify.commons.helpers.FontHelper
import org.fossify.phone.extensions.config

object HebrewFontHelper {

    private val hebrewRegex = Regex("[\u0590-\u05FF\uFB1D-\uFDFF\uFE70-\uFEFF]")

    fun getHebrewTypeface(context: Context): Typeface {
        return when (context.config.hebrewFontType) {
            1 -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            2 -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            3 -> if (context.config.hebrewFontFile.isEmpty()) {
                Typeface.DEFAULT
            } else {
                FontHelper.getTypeface(context, 2, context.config.hebrewFontFile)
            }
            else -> Typeface.DEFAULT
        }
    }

    fun applyHebrewFontToHierarchy(root: View?, context: Context) {
        if (root == null) return
        if (context.config.hebrewFontType == 0) return

        val typeface = getHebrewTypeface(context)

        fun visit(view: View) {
            if (view is TextView && !view.typeface.isBold && hebrewRegex.containsMatchIn(view.text.toString())) {
                view.typeface = typeface
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    visit(view.getChildAt(i))
                }
            }
        }

        visit(root)
    }
}
