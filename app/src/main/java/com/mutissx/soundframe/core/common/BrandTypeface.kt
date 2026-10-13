package com.mutissx.soundframe.core.common

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.mutissx.soundframe.R

/** Lazily loaded brand typeface for signatures drawn directly onto canvases and bitmaps. */
class BrandTypeface(private val context: Context) {
    val typeface: Typeface? by lazy { runCatching { ResourcesCompat.getFont(context, R.font.manrope_variable) }.getOrNull() }
}
